package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.cheer.dto.response.CheerReceiveResponse;
import com.hanol.domain.cheer.dto.response.ReceivedCheerResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.service.MoodService;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CheerReceiveConcurrencyTest {

  private static final int THREAD_COUNT = 5;
  private static final String SENDER_HASH = "receive-concurrency-sender";
  private static final String DEFAULT_MESSAGE = "기본 메시지예요.";

  @MockitoBean private DefaultCheerMessageGenerator defaultMessageGenerator;

  @Autowired private CheerReceiveService cheerReceiveService;
  @Autowired private MoodService moodService;
  @Autowired private UserRepository userRepository;
  @Autowired private CheerRepository cheerRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<Long> userIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    String recordIds = "select id from mood_records where user_id = ?";
    String blanketIds = "select id from blankets where user_id = ?";
    for (Long userId : userIds) {
      jdbcTemplate.update(
          "delete from mood_record_cards where mood_record_id in (" + recordIds + ")", userId);
      jdbcTemplate.update(
          "delete from blanket_pieces where blanket_id in (" + blanketIds + ")", userId);
      jdbcTemplate.update("delete from blankets where user_id = ?", userId);
    }
    jdbcTemplate.update("delete from cheers where sender_hash = ?", SENDER_HASH);
    for (Long userId : userIds) {
      jdbcTemplate.update("delete from warmth_map_logs where user_id = ?", userId);
      jdbcTemplate.update("delete from mood_records where user_id = ?", userId);
      jdbcTemplate.update("delete from daily_stats where user_id = ?", userId);
      jdbcTemplate.update("delete from users where id = ?", userId);
    }
    userIds.clear();
  }

  private Long createUserWithRecord(String email, Emotion emotion) {
    Long userId =
        userRepository.save(User.builder().email(email).password("encoded").build()).getId();
    userIds.add(userId);
    moodService.createRecord(userId, new MoodRecordCreateRequest(List.of(emotion), null));
    return userId;
  }

  private List<Future<CheerReceiveResponse>> runConcurrently(
      IntFunction<Callable<CheerReceiveResponse>> task) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<CheerReceiveResponse>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < THREAD_COUNT; i++) {
        Callable<CheerReceiveResponse> callable = task.apply(i);
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return callable.call();
                }));
      }
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }
    return futures;
  }

  @Test
  @DisplayName("대기 패치 1건을 여러 사용자가 동시에 받으면 실제 패치는 1명만, 나머지는 기본 메시지")
  void waitingCheerIsDeliveredOnce() throws Exception {
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(DEFAULT_MESSAGE);
    List<Long> receivers = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      receivers.add(createUserWithRecord("receive-many-" + i + "@example.com", Emotion.LONELY));
    }
    final Cheer cheer =
        cheerRepository.save(
            Cheer.builder()
                .senderHash(SENDER_HASH)
                .targetEmotion(Emotion.LONELY)
                .content("산책하면 좀 나아졌어요")
                .build());

    List<Future<CheerReceiveResponse>> futures =
        runConcurrently(i -> () -> cheerReceiveService.receive(receivers.get(i)));

    List<ReceivedCheerResponse> patches = new ArrayList<>();
    for (Future<CheerReceiveResponse> future : futures) {
      patches.addAll(future.get().patches());
    }
    assertThat(patches).hasSize(THREAD_COUNT);
    assertThat(patches).filteredOn(patch -> !patch.isDefault()).hasSize(1);
    assertThat(patches)
        .filteredOn(patch -> !patch.isDefault())
        .extracting(ReceivedCheerResponse::cheerId)
        .containsExactly(cheer.getId());
    assertThat(patches)
        .filteredOn(ReceivedCheerResponse::isDefault)
        .allSatisfy(patch -> assertThat(patch.cheerId()).isNull());
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from cheers where sender_hash = ? and status = 'DELIVERED'",
                Long.class,
                SENDER_HASH))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from blanket_pieces where cheer_id = ?",
                Long.class,
                cheer.getId()))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("같은 사용자가 같은 기록으로 동시에 받으면 한 번만 적립되고, 나머지는 그 패치를 그대로 돌려받는다")
  void sameRecordIsReceivedOnce() throws Exception {
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(DEFAULT_MESSAGE);
    Long userId = createUserWithRecord("receive-same@example.com", Emotion.TEARFUL);

    List<Future<CheerReceiveResponse>> futures =
        runConcurrently(i -> () -> cheerReceiveService.receive(userId));

    List<ReceivedCheerResponse> patches = new ArrayList<>();
    for (Future<CheerReceiveResponse> future : futures) {
      patches.addAll(future.get().patches());
    }
    assertThat(patches).hasSize(THREAD_COUNT);
    assertThat(patches).extracting(ReceivedCheerResponse::slotIndex).containsOnly(1);
    assertThat(patches).extracting(ReceivedCheerResponse::message).containsOnly(DEFAULT_MESSAGE);
    assertThat(
            jdbcTemplate.queryForList(
                "select p.slot_index from blanket_pieces p join blankets b on p.blanket_id = b.id"
                    + " where b.user_id = ?",
                Integer.class,
                userId))
        .containsExactly(1);
  }
}
