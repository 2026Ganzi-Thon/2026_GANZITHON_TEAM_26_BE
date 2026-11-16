package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.mood.entity.Emotion;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CheerServiceConcurrencyTest {

  private static final int THREAD_COUNT = 5;

  @MockitoBean private CheerReviewer cheerReviewer;

  @Autowired private CheerService cheerService;
  @Autowired private CheerSenderHasher cheerSenderHasher;
  @Autowired private UserRepository userRepository;
  @Autowired private DailyStatRepository dailyStatRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Clock clock;

  private Long userId;

  @AfterEach
  void tearDown() {
    if (userId == null) {
      return;
    }
    jdbcTemplate.update("delete from cheers where sender_hash = ?", cheerSenderHasher.hash(userId));
    jdbcTemplate.update("delete from daily_stats where user_id = ?", userId);
    jdbcTemplate.update("delete from users where id = ?", userId);
  }

  @Test
  @DisplayName("같은 사용자가 동시에 5번 보내도 sent_count 는 5, cheers 는 5행")
  void sentCountIsAccurateUnderConcurrency() throws Exception {
    given(cheerReviewer.review(anyString())).willReturn(CheerReviewResult.NORMAL);
    userId =
        userRepository
            .save(User.builder().email("cheer-concurrency@example.com").password("encoded").build())
            .getId();
    CheerSendRequest request = new CheerSendRequest(Emotion.LONELY, "산책하면 좀 나아졌어요");
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
                  return cheerService.send(userId, request);
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

    assertThat(dailyStatRepository.findByUserIdAndStatDate(userId, LocalDate.now(clock)))
        .get()
        .extracting(DailyStat::getSentCount)
        .isEqualTo(THREAD_COUNT);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from cheers where sender_hash = ? and status = 'WAITING'",
                Long.class,
                cheerSenderHasher.hash(userId)))
        .isEqualTo((long) THREAD_COUNT);
  }
}
