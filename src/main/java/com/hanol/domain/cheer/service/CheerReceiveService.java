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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** BLK-RCV-001·002 패치 받기, BLK-STACK-001 담요 적립. AI(기본 메시지 생성)는 트랜잭션 밖에서만 부른다. */
@Slf4j
@Service
@RequiredArgsConstructor
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

  public CheerReceiveResponse receive(Long userId) {
    MoodRecord record =
        moodRecordRepository
            .findFirstByUserIdAndRecordDateOrderByDailySeqDesc(userId, LocalDate.now(clock))
            .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
    List<MoodRecordCard> cards =
        moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(record.getId());
    validateReceivable(cards);

    boolean fromQueue = record.getDailySeq() <= QUEUE_RECEIVE_MAX_SEQ;
    String senderHash = cheerSenderHasher.hash(userId);
    Set<String> receivedDefaults =
        blanketPieceRepository.findDefaultContents(userId).stream()
            .map(String::strip)
            .collect(Collectors.toSet());

    Map<Emotion, String> defaults = new EnumMap<>(Emotion.class);
    for (MoodRecordCard card : cards) {
      Emotion emotion = card.getEmotion();
      if (!fromQueue
          || !cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
              emotion, CheerStatus.WAITING, senderHash)) {
        defaults.put(emotion, defaultMessageGenerator.generate(emotion, receivedDefaults));
      }
    }

    List<ReceivedCheerResponse> patches =
        transactionTemplate.execute(
            status ->
                fromQueue
                    ? receiveFromQueue(
                        userId, record.getId(), senderHash, defaults, receivedDefaults)
                    : receiveDefaultComfort(userId, record.getId(), defaults));
    return new CheerReceiveResponse(patches);
  }

  private List<ReceivedCheerResponse> receiveDefaultComfort(
      Long userId, Long recordId, Map<Emotion, String> defaults) {
    lockUser(userId);
    List<MoodRecordCard> cards = lockedCards(recordId);

    List<ReceivedCheerResponse> patches = new ArrayList<>();
    for (MoodRecordCard card : cards) {
      card.receiveDefaultComfort();
      patches.add(
          ReceivedCheerResponse.ofDefault(card.getEmotion(), defaults.get(card.getEmotion())));
    }
    log.info("Default comfort received. recordId={}, cardCount={}", recordId, cards.size());
    return patches;
  }

  private List<ReceivedCheerResponse> receiveFromQueue(
      Long userId,
      Long recordId,
      String senderHash,
      Map<Emotion, String> defaults,
      Set<String> receivedDefaults) {
    User user = lockUser(userId);
    List<MoodRecordCard> cards = lockedCards(recordId);
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

    // 사용자끼리 서로 다른 순서로 잠그다 교착되지 않도록 감정 선언 순서로 꺼낸다.
    Map<Emotion, Cheer> claimed = new EnumMap<>(Emotion.class);
    cards.stream()
        .map(MoodRecordCard::getEmotion)
        .filter(emotion -> !defaults.containsKey(emotion))
        .sorted(Comparator.naturalOrder())
        .forEach(
            emotion ->
                claim(emotion, senderHash, now).ifPresent(cheer -> claimed.put(emotion, cheer)));

    Blanket blanket =
        blanketRepository.findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(userId).orElse(null);
    int filled = blanket == null ? 0 : blanketPieceRepository.countByBlanketId(blanket.getId());

    List<ReceivedCheerResponse> patches = new ArrayList<>();
    for (MoodRecordCard card : cards) {
      Emotion emotion = card.getEmotion();
      Cheer cheer = claimed.get(emotion);
      String message;
      if (cheer != null) {
        message = cheer.getContent();
      } else if (defaults.containsKey(emotion)) {
        message = defaults.get(emotion);
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
                  .content(message)
                  .receivedAt(now)
                  .build());
      filled++;
      if (filled >= Blanket.SLOT_COUNT) {
        blanket.complete(now);
        log.info("Blanket completed. blanketId={}", blanket.getId());
        blanket = null;
      }

      card.receive(message, piece);
      patches.add(
          cheer != null
              ? ReceivedCheerResponse.ofCheer(cheer.getId(), emotion, message)
              : ReceivedCheerResponse.ofDefault(emotion, message));
    }
    log.info(
        "Cheers received. recordId={}, cardCount={}, claimedCount={}",
        recordId,
        cards.size(),
        claimed.size());
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

  private User lockUser(Long userId) {
    return userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
  }

  /** 사용자 잠금 뒤에 다시 읽어 409 를 재검사한다. 그 사이 기록이 지워졌으면 400. */
  private List<MoodRecordCard> lockedCards(Long recordId) {
    List<MoodRecordCard> cards =
        moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(recordId);
    validateReceivable(cards);
    return cards;
  }

  private static void validateReceivable(List<MoodRecordCard> cards) {
    if (cards.isEmpty()) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
    if (cards.stream().anyMatch(card -> card.getReceiveStatus() != ReceiveStatus.NOT_RECEIVED)) {
      throw new BusinessException(CommonErrorCode.CONFLICT);
    }
  }
}
