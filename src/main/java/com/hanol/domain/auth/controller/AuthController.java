package com.hanol.domain.auth.controller;

import com.hanol.domain.auth.dto.request.EmailCodeSendRequest;
import com.hanol.domain.auth.dto.request.EmailCodeVerifyRequest;
import com.hanol.domain.auth.dto.request.LoginRequest;
import com.hanol.domain.auth.dto.request.SignupRequest;
import com.hanol.domain.auth.dto.request.TokenReissueRequest;
import com.hanol.domain.auth.dto.response.LoginResponse;
import com.hanol.domain.auth.dto.response.TokenReissueResponse;
import com.hanol.domain.auth.service.AuthService;
import com.hanol.domain.auth.service.EmailVerificationService;
import com.hanol.domain.auth.service.TokenService;
import com.hanol.global.response.ApiResponse;
import com.hanol.global.security.BearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;
  private final TokenService tokenService;

  /** USR-AUTH-001 회원가입. */
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
    authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok());
  }

  /** USR-AUTH-002 이메일 인증번호 발송. */
  @PostMapping("/email-codes")
  public ApiResponse<Void> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
    emailVerificationService.sendCode(request.email());
    return ApiResponse.ok();
  }

  /** USR-AUTH-002 이메일 인증번호 확인. */
  @PostMapping("/email-codes/verify")
  public ApiResponse<Void> verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
    emailVerificationService.verifyCode(request.email(), request.code());
    return ApiResponse.ok();
  }

  /** USR-AUTH-003 로그인 (USR-AUTH-004 잠금 포함). */
  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(authService.login(request));
  }

  /** USR-AUTH-006 토큰 재발급. */
  @PostMapping("/reissue")
  public ApiResponse<TokenReissueResponse> reissue(@RequestBody TokenReissueRequest request) {
    return ApiResponse.ok(TokenReissueResponse.from(tokenService.reissue(request.refreshToken())));
  }

  /** USR-AUTH-007 로그아웃 (로그인 필요). */
  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
    tokenService.logout(userId, BearerTokenResolver.resolve(httpRequest));
    return ApiResponse.ok();
  }
}
