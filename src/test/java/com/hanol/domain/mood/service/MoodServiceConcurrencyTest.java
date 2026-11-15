package com.hanol.domain.mood.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanol.domain.mood.dto.request.MoodRecordCreateRequest;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.mood.entity.MoodRecord;
import com.hanol.domain.mood.repository.MoodRecordRepository;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

@SpringBootTest
@ActiveProfiles("test")
class MoodServiceConcurrencyTest {

  private static final int THREAD_COUNT = 5;

  @Autowired private MoodService moodService;
  @Autowired private UserRepository userRepository;
  @Autowired private MoodRecordRepository moodRecordRepository;
  @Autowired private DailyStatRepository dailyStatRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  private Long userId;

  @AfterEach
  void tearDown() {
    if (userId == null) {
      return;
    }
    String recordIds = "select id from mood_records where user_id = ?";
    jdbcTemplate.update("delete from warmth_map_logs where user_id = ?", userId);
    jdbcTemplate.update("delete from stories where mood_record_id in (" + recordIds + ")", userId);
    jdbcTemplate.update(
        "delete from mood_record_cards where mood_record_id in (" + recordIds + ")", userId);
    jdbcTemplate.update("delete from mood_records where user_id = ?", userId);
    jdbcTemplate.update("delete from daily_stats where user_id = ?", userId);
    jdbcTemplate.update("delete from users where id = ?", userId);
  }

  @Test
  @DisplayName("같은 사용자가 동시에 5번 기록해도 daily_seq 가 1~5 로 중복 없이 생기고 pick_count 는 5")
  void dailySeqIsUniqueUnderConcurrency() throws Exception {
    userId =
        userRepository
            .save(User.builder().email("concurrency@example.com").password("encoded").build())
            .getId();
    MoodRecordCreateRequest request = new MoodRecordCreateRequest(List.of(Emotion.ANXIETY), null);
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < THREAD_COUNT; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return moodService.createRecord(userId, request);
                }));
      }
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    LocalDate today = LocalDate.now(clock);
    List<MoodRecord> records =
        moodRecordRepository.findByUserIdAndRecordDateOrderByRecordedAtAsc(userId, today);
    assertThat(records)
        .extracting(MoodRecord::getDailySeq)
        .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    assertThat(dailyStatRepository.findByUserIdAndStatDate(userId, today))
        .get()
        .extracting(DailyStat::getPickCount)
        .isEqualTo(THREAD_COUNT);
  }
}
