package com.hanol.domain.auth.service;

import com.hanol.domain.auth.exception.AuthDomainErrorCode;
import com.hanol.domain.auth.repository.EmailVerificationRepository;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import com.hanol.infra.mail.MailService;
import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 이메일 인증 (USR-AUTH-002). Redis 작업이 중심이라 JPA 트랜잭션을 걸지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  static final Duration CODE_TTL = Duration.ofMinutes(5);
  static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
  static final Duration LIMIT_WINDOW = Duration.ofMinutes(5);
  static final Duration BLOCK_DURATION = Duration.ofMinutes(5);
  static final int MAX_SEND_COUNT = 5;
  static final int MAX_VERIFY_FAILURES = 5;
  private static final int CODE_BOUND = 1_000_000;

  private final SecureRandom random = new SecureRandom();

  private final UserRepository userRepository;
  private final EmailVerificationRepository emailVerificationRepository;
  private final MailService mailService;

  /** 인증번호를 저장하고 바로 반환한다. 메일은 비동기로 보낸다. */
  public void sendCode(String rawEmail) {
    String email = EmailNormalizer.normalize(rawEmail);
    checkNotBlocked(email);
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_DUPLICATED);
    }

    long sendCount = emailVerificationRepository.increaseSendCount(email, LIMIT_WINDOW);
    if (sendCount > MAX_SEND_COUNT) {
      emailVerificationRepository.deleteSendCount(email);
      block(email);
      throw new BusinessException(AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    }

    String code = String.format("%06d", random.nextInt(CODE_BOUND));
    emailVerificationRepository.saveCode(email, code, CODE_TTL);
    mailService.sendVerificationCode(email, code);
  }

  public void verifyCode(String rawEmail, String code) {
    String email = EmailNormalizer.normalize(rawEmail);
    checkNotBlocked(email);
    String savedCode =
        emailVerificationRepository
            .findCode(email)
            .orElseThrow(() -> new BusinessException(AuthDomainErrorCode.EMAIL_CODE_NOT_FOUND));

    if (!savedCode.equals(code)) {
      long failCount = emailVerificationRepository.increaseVerifyFailCount(email, LIMIT_WINDOW);
      if (failCount >= MAX_VERIFY_FAILURES) {
        emailVerificationRepository.deleteCode(email);
        emailVerificationRepository.deleteVerifyFailCount(email);
        block(email);
        throw new BusinessException(AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
      }
      throw new BusinessException(AuthDomainErrorCode.EMAIL_CODE_MISMATCH);
    }

    emailVerificationRepository.saveVerified(email, VERIFIED_TTL);
    emailVerificationRepository.deleteCode(email);
    emailVerificationRepository.deleteVerifyFailCount(email);
    log.info("이메일 인증 완료");
  }

  /** 회원가입 시 이메일 인증 완료 여부 확인. email 은 정규화된 값이어야 한다. */
  boolean isVerified(String email) {
    return emailVerificationRepository.isVerified(email);
  }

  /** 회원가입이 끝나면 인증 완료 표시를 지운다. email 은 정규화된 값이어야 한다. */
  void clearVerified(String email) {
    emailVerificationRepository.deleteVerified(email);
  }

  private void checkNotBlocked(String email) {
    if (emailVerificationRepository.isBlocked(email)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_REQUEST_LIMITED);
    }
  }

  private void block(String email) {
    emailVerificationRepository.block(email, BLOCK_DURATION);
    log.info("이메일 인증 요청 차단");
  }
}
