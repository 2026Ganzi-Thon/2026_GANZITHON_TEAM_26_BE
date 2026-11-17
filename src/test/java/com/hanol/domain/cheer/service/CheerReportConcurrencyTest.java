package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.cheer.dto.response.CheerReportResponse;
import com.hanol.domain.cheer.entity.Cheer;
import com.hanol.domain.cheer.exception.CheerErrorCode;
import com.hanol.domain.cheer.repository.CheerRepository;
import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.service.MoodService;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class CheerReportConcurrencyTest {

  private static final int THREAD_COUNT = 2;
  private static final int RESTRICTION_REPORT_COUNT = 3;
  private static final String SENDER_HASH = "report-concurrency-sender";
  private static final String DEFAULT_MESSAGE = "기본 메시지예요.";

  @MockitoBean private DefaultCheerMessageGenerator defaultMessageGenerator;

  @Autowired private CheerReportService cheerReportService;
  @Autowired private CheerReceiveService cheerReceiveService;
  @Autowired private MoodService moodService;
  @Autowired private UserRepository userRepository;
  @Autowired private CheerRepository cheerRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<Long> userIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "delete from reports where cheer_id in (select id from cheers where sender_hash = ?)",
        SENDER_HASH);
    jdbcTemplate.update("delete from send_restrictions where sender_hash = ?", SENDER_HASH);
    for (Long userId : userIds) {
      deleteUserData(userId);
    }
    jdbcTemplate.update("delete from cheers where sender_hash = ?", SENDER_HASH);
    userIds.clear();
  }

  private void deleteUserData(Long userId) {
    String recordIds = "select id from mood_records where user_id = ?";
    String blanketIds = "select id from blankets where user_id = ?";
    jdbcTemplate.update(
        "delete from mood_record_cards where mood_record_id in (" + recordIds + ")", userId);
    jdbcTemplate.update(
        "delete from blanket_pieces where blanket_id in (" + blanketIds + ")", userId);
    jdbcTemplate.update("delete from blankets where user_id = ?", userId);
    jdbcTemplate.update("delete from warmth_map_logs where user_id = ?", userId);
    jdbcTemplate.update("delete from mood_records where user_id = ?", userId);
    jdbcTemplate.update("delete from daily_stats where user_id = ?", userId);
    jdbcTemplate.update("delete from users where id = ?", userId);
  }

  @Test
  @DisplayName("같은 사용자가 같은 패치를 동시에 두 번 신고하면 1번만 성공, 나머지는 409, 칸 교체 1번")
  void sameCheerIsReportedOnce() throws Exception {
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(DEFAULT_MESSAGE);
    Long userId = createUser("report-same@example.com");
    moodService.createRecord(userId, new MoodRecordCreateRequest(List.of(Emotion.LONELY), null));
    Cheer cheer =
        cheerRepository.save(
            Cheer.builder()
                .senderHash(SENDER_HASH)
                .targetEmotion(Emotion.LONELY)
                .content("산책하면 좀 나아졌어요")
                .build());
    cheerReceiveService.receive(userId);
    Long cheerId = cheer.getId();
    for (int i = 0; i < THREAD_COUNT; i++) {
      cheerRepository.save(
          Cheer.builder()
              .senderHash(SENDER_HASH)
              .targetEmotion(Emotion.LONELY)
              .content("교체용 패치 " + i)
              .build());
    }

    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<CheerReportResponse>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < THREAD_COUNT; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return cheerReportService.report(userId, cheerId);
                }));
      }
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }

    int successCount = 0;
    int conflictCount = 0;
    for (Future<CheerReportResponse> future : futures) {
      try {
        future.get();
        successCount++;
      } catch (ExecutionException exception) {
        assertThat(exception.getCause())
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(CheerErrorCode.CHEER_ALREADY_REPORTED);
        conflictCount++;
      }
    }
    assertThat(successCount).isEqualTo(1);
    assertThat(conflictCount).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reports where cheer_id = ?", Long.class, cheerId))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from cheers where id = ?", String.class, cheerId))
        .isEqualTo("HIDDEN");
    assertThat(
            jdbcTemplate.queryForList(
                "select status from cheers where sender_hash = ? and id <> ?",
                String.class,
                SENDER_HASH,
                cheerId))
        .containsExactly("WAITING", "WAITING");
    Map<String, Object> slot =
        jdbcTemplate.queryForMap(
            "select p.cheer_id, p.content from blanket_pieces p"
                + " join blankets b on p.blanket_id = b.id where b.user_id = ?",
            userId);
    assertThat(slot.get("cheer_id")).isNull();
    assertThat(slot.get("content")).isEqualTo(DEFAULT_MESSAGE);
  }

  @Test
  @DisplayName("같은 발신자의 패치가 3명에게 신고되면(2명은 동시에) 신고 3건 모두 성공하고 보내기 제한이 걸린다")
  void restrictSenderWhenReportedThreeTimes() throws Exception {
    given(defaultMessageGenerator.generate(any(), anySet())).willReturn(DEFAULT_MESSAGE);
    for (int i = 0; i < RESTRICTION_REPORT_COUNT; i++) {
      cheerRepository.save(
          Cheer.builder()
              .senderHash(SENDER_HASH)
              .targetEmotion(Emotion.LONELY)
              .content("신고될 패치 " + i)
              .build());
    }
    List<Long> reporters = new ArrayList<>();
    List<Long> cheerIds = new ArrayList<>();
    for (int i = 0; i < RESTRICTION_REPORT_COUNT; i++) {
      Long reporter = createUser("report-restrict-" + i + "@example.com");
      moodService.createRecord(
          reporter, new MoodRecordCreateRequest(List.of(Emotion.LONELY), null));
      cheerReceiveService.receive(reporter);
      reporters.add(reporter);
      cheerIds.add(
          jdbcTemplate.queryForObject(
              "select p.cheer_id from blanket_pieces p join blankets b on p.blanket_id = b.id"
                  + " where b.user_id = ?",
              Long.class,
              reporter));
    }

    cheerReportService.report(reporters.get(0), cheerIds.get(0));
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<CheerReportResponse>> futures = new ArrayList<>();
    try {
      for (int i = 1; i <= THREAD_COUNT; i++) {
        Long reporter = reporters.get(i);
        Long cheerId = cheerIds.get(i);
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return cheerReportService.report(reporter, cheerId);
                }));
      }
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }

    for (Future<CheerReportResponse> future : futures) {
      assertThat(future.get()).isNotNull();
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from reports where sender_hash = ?", Long.class, SENDER_HASH))
        .isEqualTo(3L);
    assertThat(
            jdbcTemplate.queryForObject(
                "select restricted_until from send_restrictions where sender_hash = ?",
                LocalDateTime.class,
                SENDER_HASH))
        .isNotNull();
  }

  private Long createUser(String email) {
    Long userId =
        userRepository.save(User.builder().email(email).password("encoded").build()).getId();
    userIds.add(userId);
    return userId;
  }
}
