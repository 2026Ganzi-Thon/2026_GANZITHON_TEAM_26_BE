package com.hanol.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hanol.domain.auth.exception.AuthDomainErrorCode;
import com.hanol.domain.auth.repository.EmailVerificationRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.infra.mail.MailService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private static final String EMAIL = "user@example.com";

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationRepository emailVerificationRepository;
  @Mock private MailService mailService;

  private EmailVerificationService emailVerificationService;

  @BeforeEach
  void setUp() {
    emailVerificationService =
        new EmailVerificationService(userRepository, emailVerificationRepository, mailService);
  }

  private static void assertErrorCode(Runnable action, AuthDomainErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Test
  @DisplayName("6자리 숫자 인증번호를 5분 만료로 덮어써 저장하고, 저장한 번호로 메일을 보낸다")
  void sendCode() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(
            emailVerificationRepository.increaseSendCount(
                EMAIL, EmailVerificationService.LIMIT_WINDOW))
        .willReturn(1L);

    emailVerificationService.sendCode("User@Example.com");

    ArgumentCaptor<String> savedCode = ArgumentCaptor.forClass(String.class);
    verify(emailVerificationRepository)
        .saveCode(eq(EMAIL), savedCode.capture(), eq(EmailVerificationService.CODE_TTL));
    assertThat(savedCode.getValue()).matches("\\d{6}");
    verify(mailService).sendVerificationCode(EMAIL, savedCode.getValue());
  }

  @Test
  @DisplayName("5번째 발송까지는 허용한다")
  void sendCodeUpToLimit() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(emailVerificationRepository.increaseSendCount(eq(EMAIL), any())).willReturn(5L);

    emailVerificationService.sendCode(EMAIL);

    verify(mailService).sendVerificationCode(eq(EMAIL), anyString());
    verify(emailVerificationRepository, never()).block(anyString(), any());
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 409_EMAIL_DUPLICATED, 메일을 보내지 않는다")
  void sendCodeToRegisteredEmail() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(true);

    assertErrorCode(
        () -> emailVerificationService.sendCode(EMAIL), AuthDomainErrorCode.EMAIL_DUPLICATED);
    verify(mailService, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("6번째 발송 요청이면 5분 차단하고 429_EMAIL_REQUEST_LIMITED, 메일을 보내지 않는다")
  void sendOverLimit() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(emailVerificationRepository.increaseSendCount(eq(EMAIL), any())).willReturn(6L);

    assertErrorCode(
        () -> emailVerificationService.sendCode(EMAIL), AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    verify(emailVerificationRepository).block(EMAIL, EmailVerificationService.BLOCK_DURATION);
    verify(emailVerificationRepository, never()).saveCode(anyString(), anyString(), any());
    verify(mailService, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("차단 중이면 발송 요청도 429_EMAIL_REQUEST_LIMITED")
  void sendWhileBlocked() {
    given(emailVerificationRepository.isBlocked(EMAIL)).willReturn(true);

    assertErrorCode(
        () -> emailVerificationService.sendCode(EMAIL), AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    verify(emailVerificationRepository, never()).increaseSendCount(anyString(), any());
    verify(mailService, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("인증번호가 맞으면 인증 완료를 10분 만료로 저장하고 코드·실패 횟수를 지운다")
  void verifyCode() {
    given(emailVerificationRepository.findCode(EMAIL)).willReturn(Optional.of("123456"));

    emailVerificationService.verifyCode(EMAIL, "123456");

    verify(emailVerificationRepository).saveVerified(EMAIL, EmailVerificationService.VERIFIED_TTL);
    verify(emailVerificationRepository).deleteCode(EMAIL);
    verify(emailVerificationRepository).deleteVerifyFailCount(EMAIL);
  }

  @Test
  @DisplayName("발급 이력이 없거나 만료됐으면 404_EMAIL_CODE_NOT_FOUND")
  void verifyWithoutCode() {
    given(emailVerificationRepository.findCode(EMAIL)).willReturn(Optional.empty());

    assertErrorCode(
        () -> emailVerificationService.verifyCode(EMAIL, "123456"),
        AuthDomainErrorCode.EMAIL_CODE_NOT_FOUND);
  }

  @Test
  @DisplayName("인증번호가 다르면 400_EMAIL_CODE_MISMATCH, 인증 완료 처리하지 않는다")
  void verifyWrongCode() {
    given(emailVerificationRepository.findCode(EMAIL)).willReturn(Optional.of("123456"));
    given(emailVerificationRepository.increaseVerifyFailCount(eq(EMAIL), any())).willReturn(4L);

    assertErrorCode(
        () -> emailVerificationService.verifyCode(EMAIL, "654321"),
        AuthDomainErrorCode.EMAIL_CODE_MISMATCH);
    verify(emailVerificationRepository, never()).saveVerified(anyString(), any());
    verify(emailVerificationRepository, never()).block(anyString(), any());
  }

  @Test
  @DisplayName("5번째로 틀리면 인증번호를 지우고 5분 차단, 429_EMAIL_REQUEST_LIMITED")
  void verifyWrongCodeFifthTime() {
    given(emailVerificationRepository.findCode(EMAIL)).willReturn(Optional.of("123456"));
    given(emailVerificationRepository.increaseVerifyFailCount(eq(EMAIL), any())).willReturn(5L);

    assertErrorCode(
        () -> emailVerificationService.verifyCode(EMAIL, "654321"),
        AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    verify(emailVerificationRepository).deleteCode(EMAIL);
    verify(emailVerificationRepository).block(EMAIL, EmailVerificationService.BLOCK_DURATION);
  }

  @Test
  @DisplayName("차단 중이면 맞는 번호여도 429_EMAIL_REQUEST_LIMITED")
  void verifyWhileBlocked() {
    given(emailVerificationRepository.isBlocked(EMAIL)).willReturn(true);

    assertErrorCode(
        () -> emailVerificationService.verifyCode(EMAIL, "123456"),
        AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    verify(emailVerificationRepository, never()).saveVerified(anyString(), any());
  }
}
