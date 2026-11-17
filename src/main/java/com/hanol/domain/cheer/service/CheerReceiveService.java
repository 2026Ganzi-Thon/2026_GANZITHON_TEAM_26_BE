package com.hanol.domain.cheer.service;

import com.hanol.domain.blanket.entity.Blanket;
import com.hanol.domain.blanket.entity.BlanketPiece;
import com.hanol.domain.blanket.repository.BlanketPieceRepository;
import com.hanol.domain.blanket.repository.BlanketRepository;
import com.hanol.domain.cheer.dto.response.CheerReceiveResponse;
import com.hanol.domain.cheer.dto.response.ReceivedCheerResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.entity.CheerStatus;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.entity.MoodRecordCard;
import com.hanol.domain.mood.entity.ReceiveStatus;
import com.hanol.domain.mood.exception.MoodErrorCode;
import com.hanol.domain.mood.repository.MoodRecordCardRepository;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** BLK-RCV-001·002 패치 받기, BLK-STACK-001 담요 적립. AI(기본 메시지 생성)는 트랜잭션 밖에서만 부른다. */
@Slf4j
@Service
public class CheerReceiveService {

  static final int QUEUE_RECEIVE_MAX_SEQ = 3;
  static final int CLAIM_CANDIDATE_SIZE = 5;

  private final MoodRecordRepository moodRecordRepository;
  private final MoodRecordCardRepository moodRecordCardRepository;
  private final CheerRepository cheerRepository;
  private final BlanketRepository blanketRepository;
  private final BlanketPieceRepository blanketPieceRepository;
  private final UserRepository userRepository;
  private final CheerSenderHasher cheerSenderHasher;
  private final DefaultCheerMessageGenerator defaultMessageGenerator;
  private final CheerFallbackMessages fallbackMessages;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private final Executor executor;

  public CheerReceiveService(
      MoodRecordRepository moodRecordRepository,
      MoodRecordCardRepository moodRecordCardRepository,
      CheerRepository cheerRepository,
      BlanketRepository blanketRepository,
      BlanketPieceRepository blanketPieceRepository,
      UserRepository userRepository,
      CheerSenderHasher cheerSenderHasher,
      DefaultCheerMessageGenerator defaultMessageGenerator,
      CheerFallbackMessages fallbackMessages,
      Clock clock,
      TransactionTemplate transactionTemplate,
      @Qualifier("applicationTaskExecutor") Executor executor) {
    this.moodRecordRepository = moodRecordRepository;
    this.moodRecordCardRepository = moodRecordCardRepository;
    this.cheerRepository = cheerRepository;
    this.blanketRepository = blanketRepository;
    this.blanketPieceRepository = blanketPieceRepository;
    this.userRepository = userRepository;
    this.cheerSenderHasher = cheerSenderHasher;
    this.defaultMessageGenerator = defaultMessageGenerator;
    this.fallbackMessages = fallbackMessages;
    this.clock = clock;
    this.transactionTemplate = transactionTemplate;
    this.executor = executor;
  }

  /** 받을 카드. 처리 방식은 카드가 속한 기록의 dailySeq 로 정한다. */
  private record Target(Long cardId, Long recordId, int dailySeq, Emotion emotion) {

    boolean fromQueue() {
      return dailySeq <= QUEUE_RECEIVE_MAX_SEQ;
    }
  }

  public CheerReceiveResponse receive(Long userId) {
    List<MoodRecord> records =
        moodRecordRepository
            .findByUserIdAndRecordDateOrderByRecordedAtAsc(userId, LocalDate.now(clock))
            .stream()
            .sorted(Comparator.comparingInt(MoodRecord::getDailySeq))
            .toList();
    if (records.isEmpty()) {
      throw new BusinessException(MoodErrorCode.NO_MOOD_RECORD_TODAY);
    }

    List<Target> targets = new ArrayList<>();
    for (MoodRecord record : records) {
      for (MoodRecordCard card :
          moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(record.getId())) {
        if (card.getReceiveStatus() == ReceiveStatus.NOT_RECEIVED) {
          targets.add(
              new Target(card.getId(), record.getId(), record.getDailySeq(), card.getEmotion()));
        }
      }
    }
    // 오늘 받을 게 하나도 안 남았으면(새로고침 등) 409 대신 오늘 받은 패치를 그대로 다시 내려준다.
    // 트랜잭션 밖에서 읽은 카드는 detached 상태라 blanketPiece 지연 로딩이 깨지므로, 트랜잭션 안에서 다시 읽는다.
    if (targets.isEmpty()) {
      return new CheerReceiveResponse(
          transactionTemplate.execute(status -> toResponses(findTodayCards(records))));
    }

    String senderHash = cheerSenderHasher.hash(userId);
    Set<String> receivedDefaults = blanketPieceRepository.findReceivedDefaultMessages(userId);

    List<Target> needDefaults =
        targets.stream()
            .filter(
                target ->
                    !target.fromQueue()
                        || !cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                            target.emotion(), CheerStatus.WAITING, senderHash))
            .toList();
    Map<Long, String> defaults = generateDefaults(needDefaults, receivedDefaults);

    List<ReceivedCheerResponse> patches =
        transactionTemplate.execute(
            status -> receiveCards(userId, targets, senderHash, defaults, receivedDefaults));
    return new CheerReceiveResponse(patches);
  }

  /** 카드별 기본 메시지를 동시에 만들고 모두 끝날 때까지 기다린다. */
  private Map<Long, String> generateDefaults(List<Target> targets, Set<String> receivedDefaults) {
    Map<Long, CompletableFuture<String>> futures = new LinkedHashMap<>();
    for (Target target : targets) {
      futures.put(
          target.cardId(),
          CompletableFuture.supplyAsync(
              () -> defaultMessageGenerator.generate(target.emotion(), receivedDefaults),
              executor));
    }
    try {
      CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw exception;
    }

    Map<Long, String> defaults = new HashMap<>();
    futures.forEach((cardId, future) -> defaults.put(cardId, future.join()));
    return defaults;
  }

  private List<ReceivedCheerResponse> receiveCards(
      Long userId,
      List<Target> targets,
      String senderHash,
      Map<Long, String> defaults,
      Set<String> receivedDefaults) {
    final User user = lockUser(userId);

    // 사용자 잠금 뒤에 다시 읽어 409 를 재검사한다. 그 사이 기록이 지워졌으면 400.
    Map<Long, MoodRecordCard> reloaded =
        moodRecordCardRepository.findAllById(targets.stream().map(Target::cardId).toList()).stream()
            .collect(Collectors.toMap(MoodRecordCard::getId, Function.identity()));
    if (reloaded.isEmpty()) {
      throw new BusinessException(MoodErrorCode.NO_MOOD_RECORD_TODAY);
    }
    List<Target> receivable =
        targets.stream()
            .filter(
                target -> {
                  MoodRecordCard card = reloaded.get(target.cardId());
                  return card != null && card.getReceiveStatus() == ReceiveStatus.NOT_RECEIVED;
                })
            .toList();
    if (receivable.isEmpty()) {
      return toResponses(reloaded.values().stream().toList());
    }
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

    // 사용자끼리 서로 다른 순서로 잠그다 교착되지 않도록 감정 선언 순서, 같은 감정이면 카드 id 순으로 꺼낸다.
    Map<Long, Cheer> claimed = new HashMap<>();
    receivable.stream()
        .filter(target -> target.fromQueue() && !defaults.containsKey(target.cardId()))
        .sorted(Comparator.comparing(Target::emotion).thenComparing(Target::cardId))
        .forEach(
            target ->
                claim(target.emotion(), senderHash, now)
                    .ifPresent(cheer -> claimed.put(target.cardId(), cheer)));

    boolean hasQueueCard = receivable.stream().anyMatch(Target::fromQueue);
    Blanket blanket =
        hasQueueCard
            ? blanketRepository
                .findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(userId)
                .orElse(null)
            : null;
    int filled = blanket == null ? 0 : blanketPieceRepository.countByBlanketId(blanket.getId());

    List<ReceivedCheerResponse> patches = new ArrayList<>();
    List<Long> queueRecordIds = new ArrayList<>();
    List<Long> comfortRecordIds = new ArrayList<>();
    int queueCardCount = 0;
    int comfortCardCount = 0;
    for (Target target : receivable) {
      MoodRecordCard card = reloaded.get(target.cardId());
      Emotion emotion = target.emotion();

      if (!target.fromQueue()) {
        String comfortMessage = defaults.get(target.cardId());
        card.receiveDefaultComfort(comfortMessage);
        patches.add(ReceivedCheerResponse.ofDefault(emotion, comfortMessage));
        comfortCardCount++;
        if (!comfortRecordIds.contains(target.recordId())) {
          comfortRecordIds.add(target.recordId());
        }
        continue;
      }

      Cheer cheer = claimed.get(target.cardId());
      String message;
      if (cheer != null) {
        message = cheer.getContent();
      } else if (defaults.containsKey(target.cardId())) {
        message = defaults.get(target.cardId());
      } else {
        log.info("Cheer claim lost, using fallback. emotion={}", emotion);
        message = fallbackMessages.pick(emotion, receivedDefaults);
      }

      if (blanket == null) {
        blanket = blanketRepository.save(Blanket.builder().user(user).build());
        filled = 0;
      }
      BlanketPiece piece =
          blanketPieceRepository.save(
              BlanketPiece.builder()
                  .blanket(blanket)
                  .slotIndex(filled + 1)
                  .cheer(cheer)
                  .emotion(emotion)
                  .content(message)
                  .receivedAt(now)
                  .build());
      filled++;
      boolean blanketCompleted = filled >= Blanket.SLOT_COUNT;
      if (blanketCompleted) {
        blanket.complete(now);
        log.info("Blanket completed. blanketId={}", blanket.getId());
        blanket = null;
      }

      card.receive(piece);
      patches.add(
          cheer != null
              ? ReceivedCheerResponse.ofCheer(
                  cheer.getId(), emotion, message, piece.getSlotIndex(), blanketCompleted)
              : ReceivedCheerResponse.ofDefault(
                  emotion, message, piece.getSlotIndex(), blanketCompleted));
      queueCardCount++;
      if (!queueRecordIds.contains(target.recordId())) {
        queueRecordIds.add(target.recordId());
      }
    }

    if (queueCardCount > 0) {
      log.info(
          "Cheers received. recordIds={}, cardCount={}, claimedCount={}",
          queueRecordIds,
          queueCardCount,
          claimed.size());
    }
    if (comfortCardCount > 0) {
      log.info(
          "Default comfort received. recordIds={}, cardCount={}",
          comfortRecordIds,
          comfortCardCount);
    }
    return patches;
  }

  /** 후보를 오래된 순으로 보며 WAITING → DELIVERED 선점. 1행이 바뀐 것만 내 것이다. */
  private Optional<Cheer> claim(Emotion emotion, String senderHash, LocalDateTime now) {
    List<Cheer> candidates =
        cheerRepository.findQueueCandidates(
            emotion, CheerStatus.WAITING, senderHash, PageRequest.of(0, CLAIM_CANDIDATE_SIZE));
    for (Cheer candidate : candidates) {
      if (cheerRepository.changeStatusIf(
              candidate.getId(), CheerStatus.WAITING, CheerStatus.DELIVERED, now)
          == 1) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  /** 오늘 기록의 카드를 전부 다시 읽는다. 지연 로딩이 되도록 트랜잭션 안에서 호출해야 한다. */
  private List<MoodRecordCard> findTodayCards(List<MoodRecord> records) {
    List<MoodRecordCard> cards = new ArrayList<>();
    for (MoodRecord record : records) {
      cards.addAll(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(record.getId()));
    }
    return cards;
  }

  /** 이미 받기 처리된 카드들을 응답으로 복원한다(새로고침 등으로 다시 요청했을 때 쓴다). */
  private List<ReceivedCheerResponse> toResponses(List<MoodRecordCard> cards) {
    return cards.stream().map(this::toResponse).toList();
  }

  private ReceivedCheerResponse toResponse(MoodRecordCard card) {
    if (card.getReceiveStatus() == ReceiveStatus.DEFAULT_COMFORT) {
      return ReceivedCheerResponse.ofDefault(card.getEmotion(), card.getComfortMessage());
    }
    BlanketPiece piece = card.getBlanketPiece();
    boolean blanketCompleted = piece.getBlanket().isCompleted();
    Cheer cheer = piece.getCheer();
    return cheer == null
        ? ReceivedCheerResponse.ofDefault(
            card.getEmotion(), piece.getContent(), piece.getSlotIndex(), blanketCompleted)
        : ReceivedCheerResponse.ofCheer(
            cheer.getId(),
            card.getEmotion(),
            piece.getContent(),
            piece.getSlotIndex(),
            blanketCompleted);
  }

  private User lockUser(Long userId) {
    return userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
  }
}
