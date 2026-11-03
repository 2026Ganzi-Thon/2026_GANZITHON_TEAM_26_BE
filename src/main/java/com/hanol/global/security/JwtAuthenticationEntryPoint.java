package com.hanol.global.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.hanol.global.exception.CommonErrorCode;
import com.hanol.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그인이 필요한 API 에 인증 없이 접근했을 때 401 응답.
 * 토큰이 만료/위조된 경우 JwtAuthenticationFilter 가 남긴 에러 코드를 그대로 사용한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	public static final String ERROR_CODE_ATTRIBUTE = "jwtErrorCode";

	private final SecurityResponseWriter responseWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		Object attribute = request.getAttribute(ERROR_CODE_ATTRIBUTE);
		ErrorCode errorCode = attribute instanceof ErrorCode code ? code : CommonErrorCode.UNAUTHORIZED;
		responseWriter.write(response, errorCode);
	}
}
