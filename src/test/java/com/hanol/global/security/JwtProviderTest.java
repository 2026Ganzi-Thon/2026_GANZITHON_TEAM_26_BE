package com.hanol.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanol.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET = "3genv/NxOtjm57CHVRhOpUWETApTT7Rr4WRfHvp6kgk=";
  private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private final JwtProperties properties =
      new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(14));
  private final JwtProvider jwtProvider = new JwtProvider(properties, Clock.fixed(NOW, ZONE));

  @Test
  @DisplayName("발급한 access 토큰에서 userId 를 꺼낼 수 있다")
  void createAndParseAccessToken() {
    String token = jwtProvider.createAccessToken(1L);

    assertThat(jwtProvider.getUserId(token, TokenType.ACCESS)).isEqualTo(1L);
  }

  @Test
  @DisplayName("refresh 토큰을 access 토큰 자리에 쓰면 INVALID_TOKEN")
  void rejectWrongTokenType() {
    String refreshToken = jwtProvider.createRefreshToken(1L);

    assertThatThrownBy(() -> jwtProvider.getUserId(refreshToken, TokenType.ACCESS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("만료 시간이 지난 토큰은 EXPIRED_TOKEN")
  void rejectExpiredToken() {
    String token = jwtProvider.createAccessToken(1L);
    JwtProvider twoHoursLater =
        new JwtProvider(properties, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZONE));

    assertThatThrownBy(() -> twoHoursLater.getUserId(token, TokenType.ACCESS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
  }

  @Test
  @DisplayName("형식이 잘못된 토큰은 INVALID_TOKEN")
  void rejectMalformedToken() {
    assertThatThrownBy(() -> jwtProvider.getUserId("not-a-jwt", TokenType.ACCESS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_TOKEN);
  }
}
