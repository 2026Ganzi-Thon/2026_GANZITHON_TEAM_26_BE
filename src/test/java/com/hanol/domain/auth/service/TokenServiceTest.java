package com.hanol.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hanol.domain.auth.repository.RefreshTokenRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.security.AccessTokenStore;
import com.hanol.global.security.AuthErrorCode;
import com.hanol.global.security.JwtProperties;
import com.hanol.global.security.JwtProvider;
import com.hanol.global.security.TokenClaims;
import com.hanol.global.security.TokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
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
  private static final String SESSION_ID = "session-1";
  private static final JwtProperties PROPERTIES =
      new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(7));

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private AccessTokenStore accessTokenStore;

  private JwtProvider jwtProvider;
  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    jwtProvider = new JwtProvider(PROPERTIES, Clock.fixed(NOW, ZONE));
    tokenService =
        new TokenService(jwtProvider, PROPERTIES, refreshTokenRepository, accessTokenStore);
  }

  private void assertInvalidToken(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("발급 시 Access·Refresh 토큰에 같은 세션 id 를 넣고 두 토큰을 세션별로 7일 만료로 저장한다")
  void issue() {
    TokenPair tokens = tokenService.issue(1L);

    TokenClaims access = jwtProvider.parse(tokens.accessToken(), TokenType.ACCESS);
    TokenClaims refresh = jwtProvider.parse(tokens.refreshToken(), TokenType.REFRESH);
    assertThat(access).isEqualTo(refresh);
    assertThat(access.userId()).isEqualTo(1L);
    verify(refreshTokenRepository)
        .save(1L, access.sessionId(), tokens.refreshToken(), Duration.ofDays(7));
    verify(accessTokenStore).save(1L, access.sessionId(), tokens.accessToken(), Duration.ofDays(7));
  }

  @Test
  @DisplayName("로그인할 때마다 다른 세션 id 를 만들어 이전 기기의 세션을 덮어쓰지 않는다")
  void issueCreatesNewSessionEachLogin() {
    TokenPair first = tokenService.issue(1L);
    TokenPair second = tokenService.issue(1L);

    String firstSession = jwtProvider.parse(first.refreshToken(), TokenType.REFRESH).sessionId();
    String secondSession = jwtProvider.parse(second.refreshToken(), TokenType.REFRESH).sessionId();
    assertThat(firstSession).isNotEqualTo(secondSession);
  }

  @Test
  @DisplayName("그 세션에 저장된 Refresh 토큰과 같으면 같은 세션 id 로 새 Access 토큰을 발급해 덮어써 저장한다")
  void reissue() {
    String refreshToken = jwtProvider.createRefreshToken(1L, SESSION_ID);
    given(refreshTokenRepository.find(1L, SESSION_ID)).willReturn(Optional.of(refreshToken));

    String accessToken = tokenService.reissue(refreshToken);

    assertThat(jwtProvider.parse(accessToken, TokenType.ACCESS))
        .isEqualTo(new TokenClaims(1L, SESSION_ID));
    verify(accessTokenStore).save(1L, SESSION_ID, accessToken, Duration.ofDays(7));
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
    String refreshToken = jwtProvider.createRefreshToken(1L, SESSION_ID);
    JwtProvider eightDaysLater =
        new JwtProvider(PROPERTIES, Clock.fixed(NOW.plus(Duration.ofDays(8)), ZONE));
    TokenService laterService =
        new TokenService(eightDaysLater, PROPERTIES, refreshTokenRepository, accessTokenStore);

    assertInvalidToken(() -> laterService.reissue(refreshToken));
  }

  @Test
  @DisplayName("Access 토큰을 Refresh 자리에 넣으면 401_UNAUTHORIZED")
  void reissueWithAccessToken() {
    assertInvalidToken(() -> tokenService.reissue(jwtProvider.createAccessToken(1L, SESSION_ID)));
  }

  @Test
  @DisplayName("그 세션에 저장된 토큰이 없으면(로그아웃) 401_UNAUTHORIZED")
  void reissueAfterLogout() {
    String refreshToken = jwtProvider.createRefreshToken(1L, SESSION_ID);
    given(refreshTokenRepository.find(1L, SESSION_ID)).willReturn(Optional.empty());

    assertInvalidToken(() -> tokenService.reissue(refreshToken));
  }

  @Test
  @DisplayName("그 세션에 저장된 토큰과 다르면 401_UNAUTHORIZED")
  void reissueWithStaleToken() {
    String oldToken = jwtProvider.createRefreshToken(1L, SESSION_ID);
    given(refreshTokenRepository.find(1L, SESSION_ID)).willReturn(Optional.of("newer-token"));

    assertInvalidToken(() -> tokenService.reissue(oldToken));
  }

  @Test
  @DisplayName("로그아웃하면 그 세션의 Access·Refresh 토큰만 지운다")
  void logout() {
    String accessToken = jwtProvider.createAccessToken(1L, SESSION_ID);
    given(accessTokenStore.find(1L, SESSION_ID)).willReturn(Optional.of(accessToken));

    tokenService.logout(accessToken);

    verify(accessTokenStore).delete(1L, SESSION_ID);
    verify(refreshTokenRepository).delete(1L, SESSION_ID);
  }

  @Test
  @DisplayName("만료된 Access 토큰으로도 로그아웃할 수 있다")
  void logoutWithExpiredAccessToken() {
    String accessToken = jwtProvider.createAccessToken(1L, SESSION_ID);
    JwtProvider twoHoursLater =
        new JwtProvider(PROPERTIES, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZONE));
    TokenService laterService =
        new TokenService(twoHoursLater, PROPERTIES, refreshTokenRepository, accessTokenStore);
    given(accessTokenStore.find(1L, SESSION_ID)).willReturn(Optional.of(accessToken));

    laterService.logout(accessToken);

    verify(accessTokenStore).delete(1L, SESSION_ID);
    verify(refreshTokenRepository).delete(1L, SESSION_ID);
  }

  @Test
  @DisplayName("토큰이 없거나, 위조됐거나, Refresh 토큰이면 로그아웃 401_UNAUTHORIZED")
  void logoutWithInvalidToken() {
    assertInvalidToken(() -> tokenService.logout(null));
    assertInvalidToken(() -> tokenService.logout("not-a-jwt"));
    assertInvalidToken(() -> tokenService.logout(jwtProvider.createRefreshToken(1L, SESSION_ID)));
    verify(refreshTokenRepository, never()).delete(eq(1L), anyString());
  }

  @Test
  @DisplayName("이미 로그아웃된(세션이 지워진) 토큰으로 다시 로그아웃하면 401_UNAUTHORIZED")
  void logoutWithAlreadyLoggedOutToken() {
    String accessToken = jwtProvider.createAccessToken(1L, SESSION_ID);
    given(accessTokenStore.find(1L, SESSION_ID)).willReturn(Optional.empty());

    assertInvalidToken(() -> tokenService.logout(accessToken));

    verify(accessTokenStore, never()).delete(eq(1L), anyString());
    verify(refreshTokenRepository, never()).delete(eq(1L), anyString());
  }

  @Test
  @DisplayName("세션 id 가 없는 토큰(이전 형식)은 401_UNAUTHORIZED")
  void reissueWithoutSessionId() {
    assertInvalidToken(() -> tokenService.reissue(legacyRefreshToken()));
    verify(refreshTokenRepository, never()).find(eq(1L), anyString());
  }

  private static String legacyRefreshToken() {
    return Jwts.builder()
        .subject("1")
        .claim("type", TokenType.REFRESH.name())
        .issuedAt(Date.from(NOW))
        .expiration(Date.from(NOW.plus(Duration.ofDays(7))))
        .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
        .compact();
  }
}
