package com.hanol.global.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.hanol.global.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 발급/검증.
 * subject 에는 userId 를, type 클레임에는 ACCESS / REFRESH 를 담는다.
 */
@Component
public class JwtProvider {

	private static final String TOKEN_TYPE_CLAIM = "type";

	private final JwtProperties properties;
	private final Clock clock;
	private final SecretKey secretKey;

	public JwtProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
	}

	public String createAccessToken(Long userId) {
		return createToken(userId, TokenType.ACCESS, properties.accessTokenValidity());
	}

	public String createRefreshToken(Long userId) {
		return createToken(userId, TokenType.REFRESH, properties.refreshTokenValidity());
	}

	/**
	 * 토큰을 검증하고 userId 를 꺼낸다.
	 *
	 * @throws BusinessException 만료(EXPIRED_TOKEN)되었거나, 위조·형식 오류·타입 불일치(INVALID_TOKEN)인 경우
	 */
	public Long getUserId(String token, TokenType expectedType) {
		Claims claims = parseClaims(token);
		String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
		if (!expectedType.name().equals(tokenType)) {
			throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
		}
		return Long.valueOf(claims.getSubject());
	}

	/**
	 * 토큰의 남은 유효 시간. 로그아웃 시 Redis 블랙리스트 만료 시간으로 사용한다.
	 */
	public Duration getRemainingValidity(String token) {
		Date expiration = parseClaims(token).getExpiration();
		Duration remaining = Duration.between(clock.instant(), expiration.toInstant());
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}

	private String createToken(Long userId, TokenType tokenType, Duration validity) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim(TOKEN_TYPE_CLAIM, tokenType.name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(validity)))
			.signWith(secretKey)
			.compact();
	}

	private Claims parseClaims(String token) {
		try {
			return Jwts.parser()
				.verifyWith(secretKey)
				.clock(() -> Date.from(clock.instant()))
				.build()
				.parseSignedClaims(token)
				.getPayload();
		} catch (ExpiredJwtException exception) {
			throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
		}
	}
}
