package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
import java.util.List;
import java.util.Optional;
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
            transactionTemplate);
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
    given(blanketPieceRepository.findDefaultContents(USER_ID)).willReturn(List.of());
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(GENERATED);
    given(blanketRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(blanketPieceRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
  }

  private MoodRecord givenTodayRecord(int dailySeq) {
    MoodRecord record = MoodRecord.builder().user(user).recordedAt(NOW).dailySeq(dailySeq).build();
    ReflectionTestUtils.setField(record, "id", RECORD_ID);
    given(
            moodRecordRepository.findFirstByUserIdAndRecordDateOrderByDailySeqDesc(
                USER_ID, NOW.toLocalDate()))
        .willReturn(Optional.of(record));
    return record;
  }

  private List<MoodRecordCard> givenCards(MoodRecord record, Emotion... emotions) {
    List<MoodRecordCard> cards = new ArrayList<>();
    for (int i = 0; i < emotions.length; i++) {
      cards.add(
          MoodRecordCard.builder()
              .moodRecord(record)
              .cardOrder(i + 1)
              .emotion(emotions[i])
              .build());
    }
    given(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(RECORD_ID))
        .willReturn(cards);
    return cards;
  }

  private Cheer givenWaitingCheer(Long id, Emotion emotion, String content) {
    Cheer cheer =
        Cheer.builder().senderHash("other-hash").targetEmotion(emotion).content(content).build();
    ReflectionTestUtils.setField(cheer, "id", id);
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
    given(
            moodRecordRepository.findFirstByUserIdAndRecordDateOrderByDailySeqDesc(
                USER_ID, NOW.toLocalDate()))
        .willReturn(Optional.empty());

    assertErrorCode(() -> service.receive(USER_ID), CommonErrorCode.BAD_REQUEST);
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  @DisplayName("이미 받은 기록으로 다시 받으면 409_CONFLICT, 생성기·트랜잭션을 부르지 않는다")
  void rejectAlreadyReceived() {
    List<MoodRecordCard> cards = givenCards(givenTodayRecord(1), Emotion.LONELY);
    cards.get(0).receive("받은 메시지", null);

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
    MoodRecordCard notReceived =
        MoodRecordCard.builder().moodRecord(record).cardOrder(1).emotion(Emotion.LONELY).build();
    MoodRecordCard received =
        MoodRecordCard.builder().moodRecord(record).cardOrder(1).emotion(Emotion.LONELY).build();
    received.receive("먼저 받은 메시지", null);
    given(moodRecordCardRepository.findByMoodRecordIdOrderByCardOrderAsc(RECORD_ID))
        .willReturn(List.of(notReceived), List.of(received));
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
    assertThat(card.getReceivedMessage()).isEqualTo("산책하면 좀 나아졌어요");
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
    assertThat(cards.get(0).getReceivedMessage()).isEqualTo(GENERATED);
    verify(cheerRepository, never()).changeStatusIf(anyLong(), any(), any(), any());
  }

  @Test
  @DisplayName("4번째 이상 기록은 대기열을 보지 않고 기본 메시지, 적립 안 함, DEFAULT_COMFORT·문장 저장 안 함")
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
              assertThat(card.getReceivedMessage()).isNull();
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
    assertThat(cards.get(0).getReceivedMessage()).isEqualTo(patch.message());
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
}
