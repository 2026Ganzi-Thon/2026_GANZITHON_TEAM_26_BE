package com.hanol.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hanol.domain.auth.repository.RefreshTokenRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.security.AccessTokenBlacklist;
import com.hanol.global.security.AuthErrorCode;
import com.hanol.global.security.JwtProperties;
import com.hanol.global.security.JwtProvider;
import com.hanol.global.security.TokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

  private static final String SECRET = "3genv/NxOtjm57CHVRhOpUWETApTT7Rr4WRfHvp6kgk=";
  private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final JwtProperties PROPERTIES =
      new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(7));

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private AccessTokenBlacklist accessTokenBlacklist;

  private JwtProvider jwtProvider;
  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(PROPERTIES, Clock.fixed(NOW, ZONE));
    tokenService =
        new TokenService(jwtProvider, PROPERTIES, refreshTokenRepository, accessTokenBlacklist);
  }

  private void assertInvalidToken(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("발급 시 Refresh 토큰을 7일 만료로 Redis 에 저장한다")
  void issue() {
    TokenPair tokens = tokenService.issue(1L);

    assertThat(jwtProvider.getUserId(tokens.accessToken(), TokenType.ACCESS)).isEqualTo(1L);
    assertThat(jwtProvider.getUserId(tokens.refreshToken(), TokenType.REFRESH)).isEqualTo(1L);
    verify(refreshTokenRepository).save(1L, tokens.refreshToken(), Duration.ofDays(7));
  }

  @Test
  @DisplayName("저장된 Refresh 토큰과 같으면 새 Access 토큰을 발급한다")
  void reissue() {
    String refreshToken = jwtProvider.createRefreshToken(1L);
    given(refreshTokenRepository.find(1L)).willReturn(Optional.of(refreshToken));

    String accessToken = tokenService.reissue(refreshToken);

    assertThat(jwtProvider.getUserId(accessToken, TokenType.ACCESS)).isEqualTo(1L);
  }

  @Test
  @DisplayName("Refresh 토큰이 없으면 401_UNAUTHORIZED")
  void reissueWithoutToken() {
    assertInvalidToken(() -> tokenService.reissue(null));
    assertInvalidToken(() -> tokenService.reissue(" "));
  }

  @Test
  @DisplayName("만료된 Refresh 토큰은 401_TOKEN_EXPIRED 가 아니라 401_UNAUTHORIZED")
  void reissueWithExpiredToken() {
    String refreshToken = jwtProvider.createRefreshToken(1L);
    JwtProvider eightDaysLater =
        new JwtProvider(PROPERTIES, Clock.fixed(NOW.plus(Duration.ofDays(8)), ZONE));
    TokenService laterService =
        new TokenService(eightDaysLater, PROPERTIES, refreshTokenRepository, accessTokenBlacklist);

    assertInvalidToken(() -> laterService.reissue(refreshToken));
  }

  @Test
  @DisplayName("Access 토큰을 Refresh 자리에 넣으면 401_UNAUTHORIZED")
  void reissueWithAccessToken() {
    assertInvalidToken(() -> tokenService.reissue(jwtProvider.createAccessToken(1L)));
  }

  @Test
  @DisplayName("Redis 에 저장된 토큰이 없으면(로그아웃) 401_UNAUTHORIZED")
  void reissueAfterLogout() {
    String refreshToken = jwtProvider.createRefreshToken(1L);
    given(refreshTokenRepository.find(1L)).willReturn(Optional.empty());

    assertInvalidToken(() -> tokenService.reissue(refreshToken));
  }

  @Test
  @DisplayName("Redis 에 저장된 토큰과 다르면(다른 기기에서 재로그인 등) 401_UNAUTHORIZED")
  void reissueWithStaleToken() {
    String oldToken = jwtProvider.createRefreshToken(1L);
    given(refreshTokenRepository.find(1L)).willReturn(Optional.of("newer-token"));

    assertInvalidToken(() -> tokenService.reissue(oldToken));
  }

  @Test
  @DisplayName("로그아웃하면 Access 토큰을 남은 유효 시간만큼 블랙리스트에 올리고 Refresh 토큰을 지운다")
  void logout() {
    String accessToken = jwtProvider.createAccessToken(1L);

    tokenService.logout(1L, accessToken);

    verify(accessTokenBlacklist).add(eq(accessToken), eq(Duration.ofHours(1)));
    verify(refreshTokenRepository).delete(1L);
  }
}
