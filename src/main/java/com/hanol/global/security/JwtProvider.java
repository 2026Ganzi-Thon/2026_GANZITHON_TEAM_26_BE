package com.hanol.global.security;

import com.hanol.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 발급/검증. subject 에는 userId 를, type 클레임에는 ACCESS / REFRESH 를, sid 클레임에는 세션 id 를 담는다. */
@Component
public class JwtProvider {

  private static final String TOKEN_TYPE_CLAIM = "type";
  private static final String SESSION_ID_CLAIM = "sid";

  private final JwtProperties properties;
  private final Clock clock;
  private final SecretKey secretKey;

  public JwtProvider(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
  }

  public String createAccessToken(Long userId, String sessionId) {
    return createToken(userId, sessionId, TokenType.ACCESS, properties.accessTokenValidity());
  }

  public String createRefreshToken(Long userId, String sessionId) {
    return createToken(userId, sessionId, TokenType.REFRESH, properties.refreshTokenValidity());
  }

  /**
   * 토큰을 검증하고 userId·sessionId 를 꺼낸다.
   *
   * @throws BusinessException 만료(EXPIRED_TOKEN)되었거나, 위조·형식 오류·타입 불일치·세션 id 누락(INVALID_TOKEN)인 경우
   */
  public TokenClaims parse(String token, TokenType expectedType) {
    return toTokenClaims(parseClaims(token, false), expectedType);
  }

  /**
   * 만료된 토큰도 허용해 userId·sessionId 를 꺼낸다. (로그아웃용) 서명은 검증한다.
   *
   * @throws BusinessException 위조·형식 오류·타입 불일치·세션 id 누락(INVALID_TOKEN)인 경우
   */
  public TokenClaims parseAllowingExpired(String token, TokenType expectedType) {
    return toTokenClaims(parseClaims(token, true), expectedType);
  }

  private TokenClaims toTokenClaims(Claims claims, TokenType expectedType) {
    String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
    String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
    if (!expectedType.name().equals(tokenType) || sessionId == null) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
    return new TokenClaims(Long.valueOf(claims.getSubject()), sessionId);
  }

  private String createToken(
      Long userId, String sessionId, TokenType tokenType, Duration validity) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim(TOKEN_TYPE_CLAIM, tokenType.name())
        .claim(SESSION_ID_CLAIM, sessionId)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(validity)))
        .signWith(secretKey)
        .compact();
  }

  private Claims parseClaims(String token, boolean allowExpired) {
    try {
      return Jwts.parser()
          .verifyWith(secretKey)
          .clock(() -> Date.from(clock.instant()))
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (ExpiredJwtException exception) {
      // 서명 검증 뒤에 만료를 검사하므로, 여기까지 왔다면 서명은 올바르다.
      if (allowExpired) {
        return exception.getClaims();
      }
      throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
    } catch (JwtException | IllegalArgumentException exception) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
  }
}
