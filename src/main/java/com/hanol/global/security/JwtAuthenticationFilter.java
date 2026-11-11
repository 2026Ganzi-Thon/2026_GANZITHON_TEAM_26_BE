package com.hanol.global.security;

import com.hanol.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 읽어 로그인 사용자를 설정한다. 컨트롤러에서는 @AuthenticationPrincipal Long
 * userId 로 꺼내 쓴다. 토큰이 잘못된 경우 바로 막지 않고 에러 코드만 기록해 두며, 로그인이 필요한 API 라면 JwtAuthenticationEntryPoint 가
 * 그 에러 코드로 401 응답을 보낸다. 로그아웃해서 블랙리스트에 오른 토큰은 위조 토큰과 같이 401_UNAUTHORIZED 로 처리한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final AccessTokenBlacklist accessTokenBlacklist;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = BearerTokenResolver.resolve(request);
    if (token != null) {
      try {
        Long userId = jwtProvider.parse(token, TokenType.ACCESS).userId();
        if (accessTokenBlacklist.contains(token)) {
          throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (BusinessException exception) {
        request.setAttribute(
            JwtAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, exception.getErrorCode());
      }
    }
    filterChain.doFilter(request, response);
  }
}
