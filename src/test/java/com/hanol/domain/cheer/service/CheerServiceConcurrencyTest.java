package com.hanol.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.hanol.domain.cheer.dto.request.CheerSendRequest;
import com.hanol.domain.cheer.exception.CheerErrorCode;
import com.hanol.domain.mood.entity.Emotion;
import com.hanol.domain.user.entity.DailyStat;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.DailyStatRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
  @DisplayName("같은 사용자가 동시에 5번 보내면 1번만 성공하고 나머지 4번은 429_CHEER_COOLDOWN")
  void onlyOneSendSucceedsUnderConcurrency() throws Exception {
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
    int successCount = 0;
    List<ErrorCode> errorCodes = new ArrayList<>();

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
        try {
          future.get(30, TimeUnit.SECONDS);
          successCount++;
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(BusinessException.class);
          errorCodes.add(((BusinessException) e.getCause()).getErrorCode());
        }
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(successCount).isEqualTo(1);
    assertThat(errorCodes).hasSize(THREAD_COUNT - 1).containsOnly(CheerErrorCode.CHEER_COOLDOWN);
    assertThat(dailyStatRepository.findByUserIdAndStatDate(userId, LocalDate.now(clock)))
        .get()
        .extracting(DailyStat::getSentCount)
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from cheers where sender_hash = ? and status = 'WAITING'",
                Long.class,
                cheerSenderHasher.hash(userId)))
        .isEqualTo(1L);
  }
}
