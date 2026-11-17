package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import com.hanol.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheerReceiveServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-04T00:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
  private static final Long USER_ID = 1L;
  private static final Long RECORD_ID = 10L;
  private static final Long BLANKET_ID = 100L;
  private static final String MY_HASH = "my-hash";
  private static final String GENERATED = "AI 가 만든 기본 메시지예요.";

  @Mock private MoodRecordRepository moodRecordRepository;
  @Mock private MoodRecordCardRepository moodRecordCardRepository;
  @Mock private CheerRepository cheerRepository;
  @Mock private BlanketRepository blanketRepository;
  @Mock private BlanketPieceRepository blanketPieceRepository;
  @Mock private UserRepository userRepository;
  @Mock private CheerSenderHasher cheerSenderHasher;
  @Mock private DefaultCheerMessageGenerator defaultMessageGenerator;
  @Mock private TransactionTemplate transactionTemplate;

  private final CheerFallbackMessages fallbackMessages = new CheerFallbackMessages();
  private final List<MoodRecordCard> allCards = new ArrayList<>();
  private CheerReceiveService service;
  private User user;

  @BeforeEach
  void setUp() {
    service =
        new CheerReceiveService(
            moodRecordRepository,
            moodRecordCardRepository,
            cheerRepository,
            blanketRepository,
            blanketPieceRepository,
            userRepository,
            cheerSenderHasher,
            defaultMessageGenerator,
            fallbackMessages,
            Clock.fixed(NOW_INSTANT, ZONE),
            transactionTemplate,
            Runnable::run);
    user = User.builder().email("user@example.com").password("encoded").build();
    ReflectionTestUtils.setField(user, "id", USER_ID);

    given(transactionTemplate.execute(any()))
        .willAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
    given(cheerSenderHasher.hash(USER_ID)).willReturn(MY_HASH);
    given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
    given(blanketPieceRepository.findReceivedDefaultMessages(USER_ID)).willReturn(Set.of());
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(GENERATED);
    given(blanketRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(blanketPieceRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(moodRecordCardRepository.findAllById(any()))
        .willAnswer(
            invocation -> {
              Set<Long> ids = new HashSet<>();
              Iterable<Long> argument = invocation.getArgument(0);
              argument.forEach(ids::add);
              return allCards.stream().filter(card -> ids.contains(card.getId())).toList();
            });
  }

  private MoodRecord record(Long id, int dailySeq) {
    MoodRecord record =
        MoodRecord.builder()
            .user(user)
            .recordedAt(NOW.plusMinutes(dailySeq))
            .dailySeq(dailySeq)
            .build();
    ReflectionTestUtils.setField(record, "id", id);
    return record;
  }

  private void givenTodayRecords(MoodRecord... records) {
    given(
            moodRecordRepository.findByUserIdAndRecordDateOrderByRecordedAtAsc(
                USER_ID, NOW.toLocalDate()))
        .willReturn(List.of(records));
  }

  private MoodRecord givenTodayRecord(int dailySeq) {
    MoodRecord record = record(RECORD_ID, dailySeq);
    givenTodayRecords(record);
    return record;
  }

  /** 카드 id 는 기록 id * 10 + card_order. */
  private List<MoodRecordCard> givenCards(MoodRecord record, Emotion... emotions) {
    List<MoodRecordCard> cards = new ArrayList<>();
    for (int i = 0; i < emotions.length; i++) {
      MoodRecordCard card =
          MoodRecordCard.builder().moodRecord(record).cardOrder(i + 1).emotion(emotions[i]).build();
      ReflectionTestUtils.setField(card, "id", record.getId() * 10 + i + 1);
      cards.add(card);
    }
    allCards.addAll(cards);
    given(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(record.getId()))
        .willReturn(cards);
    return cards;
  }

  private static Cheer cheer(Long id, Emotion emotion, String content) {
    Cheer cheer =
        Cheer.builder().senderHash("other-hash").targetEmotion(emotion).content(content).build();
    ReflectionTestUtils.setField(cheer, "id", id);
    return cheer;
  }

  private Cheer givenWaitingCheer(Long id, Emotion emotion, String content) {
    Cheer cheer = cheer(id, emotion, content);
    given(
            cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                emotion, CheerStatus.WAITING, MY_HASH))
        .willReturn(true);
    given(
            cheerRepository.findQueueCandidates(
                eq(emotion), eq(CheerStatus.WAITING), eq(MY_HASH), any()))
        .willReturn(List.of(cheer));
    given(cheerRepository.changeStatusIf(id, CheerStatus.WAITING, CheerStatus.DELIVERED, NOW))
        .willReturn(1);
    return cheer;
  }

  private Blanket givenOpenBlanket(int filled) {
    Blanket blanket = Blanket.builder().user(user).build();
    ReflectionTestUtils.setField(blanket, "id", BLANKET_ID);
    given(blanketRepository.findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(USER_ID))
        .willReturn(Optional.of(blanket));
    given(blanketPieceRepository.countByBlanketId(BLANKET_ID)).willReturn(filled);
    return blanket;
  }

  private List<BlanketPiece> savedPieces() {
    ArgumentCaptor<BlanketPiece> captor = ArgumentCaptor.forClass(BlanketPiece.class);
    verify(blanketPieceRepository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues();
  }

  private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Test
  @DisplayName("오늘 기록이 없으면 400_BAD_REQUEST")
  void rejectWithoutTodayRecord() {
    givenTodayRecords();

    assertErrorCode(() -> service.receive(USER_ID), CommonErrorCode.BAD_REQUEST);
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  @DisplayName("이미 받은 기록으로 다시 받으면 409_CONFLICT, 생성기·트랜잭션을 부르지 않는다")
  void rejectAlreadyReceived() {
    List<MoodRecordCard> cards = givenCards(givenTodayRecord(1), Emotion.LONELY);
    cards.get(0).receive(null);

    assertErrorCode(() -> service.receive(USER_ID), CommonErrorCode.CONFLICT);
    verify(defaultMessageGenerator, never()).generate(any(), anySet());
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  @DisplayName("4번째 이상 기록으로 다시 받으면 409_CONFLICT")
  void rejectDefaultComfortAgain() {
    List<MoodRecordCard> cards = givenCards(givenTodayRecord(4), Emotion.LONELY);
    cards.get(0).receiveDefaultComfort();

    assertErrorCode(() -> service.receive(USER_ID), CommonErrorCode.CONFLICT);
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  @DisplayName("트랜잭션 안에서 다시 읽었을 때 이미 받았으면 409_CONFLICT, 꺼내기·적립하지 않는다")
  void rejectWhenReceivedWhileWaitingLock() {
    MoodRecord record = givenTodayRecord(1);
    MoodRecordCard notReceived = givenCards(record, Emotion.LONELY).get(0);
    MoodRecordCard received =
        MoodRecordCard.builder().moodRecord(record).cardOrder(1).emotion(Emotion.LONELY).build();
    ReflectionTestUtils.setField(received, "id", notReceived.getId());
    received.receive(null);
    willReturn(List.of(received)).given(moodRecordCardRepository).findAllById(any());
    givenWaitingCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요");

    assertErrorCode(() -> service.receive(USER_ID), CommonErrorCode.CONFLICT);
    verify(cheerRepository, never()).changeStatusIf(anyLong(), any(), any(), any());
    verify(blanketPieceRepository, never()).save(any());
  }

  @Test
  @DisplayName("1~3번째 기록은 대기 패치를 받아 카드를 RECEIVED 로 바꾸고 담요에 적립한다")
  void receiveCheerFromQueue() {
    final List<MoodRecordCard> cards = givenCards(givenTodayRecord(2), Emotion.LONELY);
    givenWaitingCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요");
    Blanket blanket = givenOpenBlanket(3);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(ReceivedCheerResponse.ofCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요"));
    BlanketPiece piece = savedPieces().get(0);
    assertThat(piece.getBlanket()).isSameAs(blanket);
    assertThat(piece.getSlotIndex()).isEqualTo(4);
    assertThat(piece.getCheer().getId()).isEqualTo(7L);
    assertThat(piece.getContent()).isEqualTo("산책하면 좀 나아졌어요");
    assertThat(piece.getReceivedAt()).isEqualTo(NOW);
    MoodRecordCard card = cards.get(0);
    assertThat(card.getReceiveStatus()).isEqualTo(ReceiveStatus.RECEIVED);
    assertThat(card.getBlanketPiece()).isSameAs(piece);
    verify(defaultMessageGenerator, never()).generate(any(), anySet());
  }

  @Test
  @DisplayName("대기열이 비면 기본 메시지를 받고, 담요에 적립하며 cheerId 는 null")
  void receiveDefaultWhenQueueEmpty() {
    final List<MoodRecordCard> cards = givenCards(givenTodayRecord(1), Emotion.ANXIETY);
    givenOpenBlanket(0);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(ReceivedCheerResponse.ofDefault(Emotion.ANXIETY, GENERATED));
    BlanketPiece piece = savedPieces().get(0);
    assertThat(piece.getCheer()).isNull();
    assertThat(piece.getSlotIndex()).isEqualTo(1);
    assertThat(piece.getContent()).isEqualTo(GENERATED);
    assertThat(cards.get(0).getReceiveStatus()).isEqualTo(ReceiveStatus.RECEIVED);
    assertThat(cards.get(0).getBlanketPiece()).isSameAs(piece);
    verify(cheerRepository, never()).changeStatusIf(anyLong(), any(), any(), any());
  }

  @Test
  @DisplayName("4번째 이상 기록은 대기열을 보지 않고 기본 메시지, 적립 안 함, DEFAULT_COMFORT·담요 칸 연결 안 함")
  void receiveDefaultComfortFromFourthRecord() {
    List<MoodRecordCard> cards = givenCards(givenTodayRecord(4), Emotion.LONELY, Emotion.TEARFUL);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(
            ReceivedCheerResponse.ofDefault(Emotion.LONELY, GENERATED),
            ReceivedCheerResponse.ofDefault(Emotion.TEARFUL, GENERATED));
    assertThat(cards)
        .allSatisfy(
            card -> {
              assertThat(card.getReceiveStatus()).isEqualTo(ReceiveStatus.DEFAULT_COMFORT);
              assertThat(card.getBlanketPiece()).isNull();
            });
    verify(cheerRepository, never())
        .existsByTargetEmotionAndStatusAndSenderHashNot(any(), any(), any());
    verify(cheerRepository, never()).findQueueCandidates(any(), any(), any(), any());
    verify(blanketRepository, never()).save(any());
    verify(blanketPieceRepository, never()).save(any());
    verify(userRepository).findByIdForUpdate(USER_ID);
  }

  @Test
  @DisplayName("다른 사람이 먼저 꺼내 선점에 실패하면 AI 를 다시 부르지 않고 대체 문구로 적립")
  void fallbackWhenClaimLost() {
    final List<MoodRecordCard> cards = givenCards(givenTodayRecord(1), Emotion.LONELY);
    givenWaitingCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요");
    given(cheerRepository.changeStatusIf(7L, CheerStatus.WAITING, CheerStatus.DELIVERED, NOW))
        .willReturn(0);
    givenOpenBlanket(0);

    CheerReceiveResponse response = service.receive(USER_ID);

    ReceivedCheerResponse patch = response.patches().get(0);
    assertThat(patch.isDefault()).isTrue();
    assertThat(patch.cheerId()).isNull();
    assertThat(fallbackMessages.of(Emotion.LONELY)).contains(patch.message());
    assertThat(savedPieces().get(0).getCheer()).isNull();
    assertThat(cards.get(0).getBlanketPiece().getContent()).isEqualTo(patch.message());
    verify(defaultMessageGenerator, never()).generate(any(), anySet());
  }

  @Test
  @DisplayName("29칸 담요에 30번째를 적립하면 같은 처리에서 완성된다")
  void completeBlanketAtThirtiethSlot() {
    givenCards(givenTodayRecord(1), Emotion.LONELY);
    Blanket blanket = givenOpenBlanket(29);

    service.receive(USER_ID);

    assertThat(savedPieces()).extracting(BlanketPiece::getSlotIndex).containsExactly(30);
    assertThat(blanket.getCompletedAt()).isEqualTo(NOW);
    verify(blanketRepository, never()).save(any());
  }

  @Test
  @DisplayName("30번째 칸을 채운 다음 카드는 새 담요의 1번 칸에 적립된다")
  void nextPieceGoesToNewBlanket() {
    givenCards(givenTodayRecord(1), Emotion.LONELY, Emotion.TEARFUL);
    Blanket blanket = givenOpenBlanket(29);

    service.receive(USER_ID);

    List<BlanketPiece> pieces = savedPieces();
    assertThat(pieces).extracting(BlanketPiece::getSlotIndex).containsExactly(30, 1);
    assertThat(pieces.get(0).getBlanket()).isSameAs(blanket);
    assertThat(pieces.get(1).getBlanket()).isNotSameAs(blanket);
    assertThat(pieces.get(1).getBlanket().isCompleted()).isFalse();
    assertThat(blanket.isCompleted()).isTrue();
  }

  @Test
  @DisplayName("완성 안 된 담요가 없으면 새로 만들어 1번 칸부터 적립한다")
  void createBlanketWhenNoneOpen() {
    givenCards(givenTodayRecord(1), Emotion.LONELY);
    given(blanketRepository.findFirstByUserIdAndCompletedAtIsNullOrderByIdDesc(USER_ID))
        .willReturn(Optional.empty());

    service.receive(USER_ID);

    assertThat(savedPieces()).extracting(BlanketPiece::getSlotIndex).containsExactly(1);
    verify(blanketRepository).save(any());
  }

  @Test
  @DisplayName("카드가 2개면 같은 요청에서 연속 2칸에 card_order 순서로 적립한다")
  void twoCardsTakeConsecutiveSlots() {
    givenCards(givenTodayRecord(3), Emotion.TEARFUL, Emotion.ANXIETY);
    givenWaitingCheer(7L, Emotion.TEARFUL, "울고 나니 좀 나았어요");
    givenOpenBlanket(5);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .extracting(ReceivedCheerResponse::emotion)
        .containsExactly(Emotion.TEARFUL, Emotion.ANXIETY);
    assertThat(savedPieces())
        .extracting(BlanketPiece::getSlotIndex, BlanketPiece::getContent)
        .containsExactly(tuple(6, "울고 나니 좀 나았어요"), tuple(7, GENERATED));
  }

  @Test
  @DisplayName("오늘 기록 2개를 둘 다 안 받았으면 기록 순·카드 순으로 받고 담요 칸이 연속으로 적립된다")
  void receiveAllPendingRecords() {
    MoodRecord first = record(11L, 1);
    MoodRecord second = record(12L, 2);
    givenTodayRecords(first, second);
    final List<MoodRecordCard> firstCards = givenCards(first, Emotion.LONELY, Emotion.ANXIETY);
    final List<MoodRecordCard> secondCards = givenCards(second, Emotion.TEARFUL);
    givenWaitingCheer(7L, Emotion.TEARFUL, "울고 나니 좀 나았어요");
    givenOpenBlanket(5);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(
            ReceivedCheerResponse.ofDefault(Emotion.LONELY, GENERATED),
            ReceivedCheerResponse.ofDefault(Emotion.ANXIETY, GENERATED),
            ReceivedCheerResponse.ofCheer(7L, Emotion.TEARFUL, "울고 나니 좀 나았어요"));
    assertThat(savedPieces())
        .extracting(BlanketPiece::getSlotIndex, BlanketPiece::getContent)
        .containsExactly(tuple(6, GENERATED), tuple(7, GENERATED), tuple(8, "울고 나니 좀 나았어요"));
    assertThat(firstCards)
        .extracting(MoodRecordCard::getReceiveStatus)
        .containsOnly(ReceiveStatus.RECEIVED);
    assertThat(secondCards.get(0).getReceiveStatus()).isEqualTo(ReceiveStatus.RECEIVED);
  }

  @Test
  @DisplayName("이미 받은 기록과 안 받은 기록이 있으면 안 받은 기록의 카드만 받는다")
  void receiveOnlyPendingRecord() {
    MoodRecord first = record(11L, 1);
    MoodRecord second = record(12L, 2);
    givenTodayRecords(first, second);
    MoodRecordCard received = givenCards(first, Emotion.LONELY).get(0);
    received.receive(null);
    final MoodRecordCard pending = givenCards(second, Emotion.TEARFUL).get(0);
    givenOpenBlanket(2);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(ReceivedCheerResponse.ofDefault(Emotion.TEARFUL, GENERATED));
    assertThat(savedPieces()).extracting(BlanketPiece::getSlotIndex).containsExactly(3);
    assertThat(pending.getReceiveStatus()).isEqualTo(ReceiveStatus.RECEIVED);
    assertThat(received.getBlanketPiece()).isNull();
    verify(defaultMessageGenerator, times(1)).generate(any(), anySet());
  }

  @Test
  @DisplayName("3번째·4번째 기록이 같이 남아 있으면 3번째 카드는 적립, 4번째 카드는 DEFAULT_COMFORT·적립 안 함")
  void mixQueueAndDefaultComfort() {
    MoodRecord third = record(13L, 3);
    MoodRecord fourth = record(14L, 4);
    givenTodayRecords(third, fourth);
    final MoodRecordCard thirdCard = givenCards(third, Emotion.LONELY).get(0);
    final MoodRecordCard fourthCard = givenCards(fourth, Emotion.TEARFUL).get(0);
    givenWaitingCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요");
    givenWaitingCheer(8L, Emotion.TEARFUL, "울고 나니 좀 나았어요");
    givenOpenBlanket(0);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(
            ReceivedCheerResponse.ofCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요"),
            ReceivedCheerResponse.ofDefault(Emotion.TEARFUL, GENERATED));
    List<BlanketPiece> pieces = savedPieces();
    assertThat(pieces).hasSize(1);
    assertThat(thirdCard.getReceiveStatus()).isEqualTo(ReceiveStatus.RECEIVED);
    assertThat(thirdCard.getBlanketPiece()).isSameAs(pieces.get(0));
    assertThat(fourthCard.getReceiveStatus()).isEqualTo(ReceiveStatus.DEFAULT_COMFORT);
    assertThat(fourthCard.getBlanketPiece()).isNull();
    verify(cheerRepository, never()).changeStatusIf(eq(8L), any(), any(), any());
  }

  @Test
  @DisplayName("두 기록에 같은 감정 카드가 있으면 카드마다 따로 꺼내 서로 다른 패치를 받는다")
  void claimPerCardForSameEmotion() {
    MoodRecord first = record(11L, 1);
    MoodRecord second = record(12L, 2);
    givenTodayRecords(first, second);
    givenCards(first, Emotion.LONELY);
    givenCards(second, Emotion.LONELY);
    Cheer older = cheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요");
    Cheer newer = cheer(8L, Emotion.LONELY, "친구에게 전화했어요");
    given(
            cheerRepository.existsByTargetEmotionAndStatusAndSenderHashNot(
                Emotion.LONELY, CheerStatus.WAITING, MY_HASH))
        .willReturn(true);
    given(
            cheerRepository.findQueueCandidates(
                eq(Emotion.LONELY), eq(CheerStatus.WAITING), eq(MY_HASH), any()))
        .willReturn(List.of(older), List.of(newer));
    given(cheerRepository.changeStatusIf(anyLong(), eq(CheerStatus.WAITING), any(), eq(NOW)))
        .willReturn(1);
    givenOpenBlanket(0);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(
            ReceivedCheerResponse.ofCheer(7L, Emotion.LONELY, "산책하면 좀 나아졌어요"),
            ReceivedCheerResponse.ofCheer(8L, Emotion.LONELY, "친구에게 전화했어요"));
    assertThat(savedPieces()).extracting(piece -> piece.getCheer().getId()).containsExactly(7L, 8L);
    verify(cheerRepository, times(2)).findQueueCandidates(any(), any(), any(), any());
    verify(defaultMessageGenerator, never()).generate(any(), anySet());
  }

  @Test
  @DisplayName("대기열이 빈 같은 감정 카드가 2개면 기본 메시지 생성도 2번이다")
  void generateDefaultPerCard() {
    MoodRecord first = record(11L, 1);
    MoodRecord second = record(12L, 2);
    givenTodayRecords(first, second);
    givenCards(first, Emotion.TEARFUL);
    givenCards(second, Emotion.TEARFUL);
    givenOpenBlanket(0);

    CheerReceiveResponse response = service.receive(USER_ID);

    assertThat(response.patches())
        .containsExactly(
            ReceivedCheerResponse.ofDefault(Emotion.TEARFUL, GENERATED),
            ReceivedCheerResponse.ofDefault(Emotion.TEARFUL, GENERATED));
    verify(defaultMessageGenerator, times(2)).generate(eq(Emotion.TEARFUL), anySet());
    verify(cheerRepository, never()).findQueueCandidates(any(), any(), any(), any());
  }
}
