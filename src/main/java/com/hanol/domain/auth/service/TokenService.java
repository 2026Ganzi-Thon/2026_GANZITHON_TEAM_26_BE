package com.hanol.domain.auth.service;

import com.hanol.domain.auth.repository.RefreshTokenRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.global.security.AccessTokenStore;
import com.hanol.global.security.AuthErrorCode;
import com.hanol.global.security.JwtProperties;
import com.hanol.global.security.JwtProvider;
import com.hanol.global.security.TokenClaims;
import com.hanol.global.security.TokenType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 토큰 발급(로그인), 재발급(USR-AUTH-006), 로그아웃(USR-AUTH-007). Redis 만 사용하므로 JPA 트랜잭션을 걸지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AccessTokenStore accessTokenStore;

  /** 새 세션을 만들어 Access·Refresh 토큰을 발급한다. 다른 기기의 세션은 그대로 둔다. */
  public TokenPair issue(Long userId) {
    String sessionId = UUID.randomUUID().toString();
    String accessToken = jwtProvider.createAccessToken(userId, sessionId);
    String refreshToken = jwtProvider.createRefreshToken(userId, sessionId);
    saveAccessToken(userId, sessionId, accessToken);
    refreshTokenRepository.save(
        userId, sessionId, refreshToken, jwtProperties.refreshTokenValidity());
    return new TokenPair(accessToken, refreshToken);
  }

  /**
   * 새 Access 토큰을 발급한다. 그 세션의 이전 Access 토큰은 바로 무효가 된다. Refresh 토큰은 교체하지 않는다(Rotation 미적용).
   *
   * <p>Refresh 토큰이 만료된 경우도 401_TOKEN_EXPIRED 가 아니라 401_UNAUTHORIZED 로 응답한다. 클라이언트는 TOKEN_EXPIRED 를
   * 받으면 재발급을 시도하므로, 재발급 API 에서 그 코드를 주면 재발급을 무한 반복하게 된다.
   */
  public String reissue(String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }

    TokenClaims claims;
    try {
      claims = jwtProvider.parse(refreshToken, TokenType.REFRESH);
    } catch (BusinessException exception) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }

    String savedToken =
        refreshTokenRepository
            .find(claims.userId(), claims.sessionId())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));
    if (!savedToken.equals(refreshToken)) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }

    String accessToken = jwtProvider.createAccessToken(claims.userId(), claims.sessionId());
    saveAccessToken(claims.userId(), claims.sessionId(), accessToken);
    log.info("Access 토큰 재발급: userId={}", claims.userId());
    return accessToken;
  }

  /** 그 세션의 Access·Refresh 토큰만 지운다. 만료된 Access 토큰으로도 로그아웃할 수 있다. */
  public void logout(String accessToken) {
    if (!StringUtils.hasText(accessToken)) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
    TokenClaims claims = jwtProvider.parseAllowingExpired(accessToken, TokenType.ACCESS);
    accessTokenStore.delete(claims.userId(), claims.sessionId());
    refreshTokenRepository.delete(claims.userId(), claims.sessionId());
    log.info("로그아웃: userId={}", claims.userId());
  }

  private void saveAccessToken(Long userId, String sessionId, String accessToken) {
    accessTokenStore.save(userId, sessionId, accessToken, jwtProperties.refreshTokenValidity());
  }
}
