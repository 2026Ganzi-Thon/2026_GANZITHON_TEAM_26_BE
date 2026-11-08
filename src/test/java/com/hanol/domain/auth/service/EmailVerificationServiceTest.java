package com.hanol.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hanol.domain.auth.exception.AuthDomainErrorCode;
import com.hanol.domain.auth.repository.EmailVerificationRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.infra.mail.MailService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private static final String EMAIL = "user@example.com";

  @Mock private UserRepository userRepository;
  @Mock private EmailVerificationRepository emailVerificationRepository;
  @Mock private MailService mailService;

  @InjectMocks private EmailVerificationService emailVerificationService;

  private static void assertErrorCode(Runnable action, AuthDomainErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Test
  @DisplayName("6자리 숫자 인증번호를 5분 만료로 저장하고, 저장한 번호로 메일을 보낸다")
  void sendCode() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(
            emailVerificationRepository.saveCodeIfAbsent(
                eq(EMAIL), anyString(), eq(EmailVerificationService.CODE_TTL)))
        .willReturn(true);

    emailVerificationService.sendCode("User@Example.com");

    ArgumentCaptor<String> savedCode = ArgumentCaptor.forClass(String.class);
    verify(emailVerificationRepository)
        .saveCodeIfAbsent(eq(EMAIL), savedCode.capture(), eq(EmailVerificationService.CODE_TTL));
    assertThat(savedCode.getValue()).matches("\\d{6}");
    verify(mailService).sendVerificationCode(EMAIL, savedCode.getValue());
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
  @DisplayName("5분 안에 다시 요청하면(코드 키가 살아 있으면) 429_EMAIL_CODE_ALREADY_SENT")
  void resendWithinTtl() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(emailVerificationRepository.saveCodeIfAbsent(eq(EMAIL), anyString(), any()))
        .willReturn(false);

    assertErrorCode(
        () -> emailVerificationService.sendCode(EMAIL),
        AuthDomainErrorCode.EMAIL_CODE_ALREADY_SENT);
    verify(mailService, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("메일 발송에 실패하면 저장한 코드를 지우고 500_MAIL_SEND_FAILED")
  void mailSendFailure() {
    given(userRepository.existsByEmail(EMAIL)).willReturn(false);
    given(emailVerificationRepository.saveCodeIfAbsent(eq(EMAIL), anyString(), any()))
        .willReturn(true);
    willThrow(new MailSendException("smtp down"))
        .given(mailService)
        .sendVerificationCode(eq(EMAIL), anyString());

    assertErrorCode(
        () -> emailVerificationService.sendCode(EMAIL), AuthDomainErrorCode.MAIL_SEND_FAILED);
    verify(emailVerificationRepository).deleteCode(EMAIL);
  }

  @Test
  @DisplayName("인증번호가 맞으면 인증 완료를 10분 만료로 저장하고 코드 키를 지운다")
  void verifyCode() {
    given(emailVerificationRepository.findCode(EMAIL)).willReturn(Optional.of("123456"));

    emailVerificationService.verifyCode(EMAIL, "123456");

    verify(emailVerificationRepository).saveVerified(EMAIL, EmailVerificationService.VERIFIED_TTL);
    verify(emailVerificationRepository).deleteCode(EMAIL);
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

    assertErrorCode(
        () -> emailVerificationService.verifyCode(EMAIL, "654321"),
        AuthDomainErrorCode.EMAIL_CODE_MISMATCH);
    verify(emailVerificationRepository, never()).saveVerified(anyString(), any());
  }
}
