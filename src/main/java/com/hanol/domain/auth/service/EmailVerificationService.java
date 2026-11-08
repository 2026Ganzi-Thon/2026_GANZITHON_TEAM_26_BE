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
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/** 이메일 인증 (USR-AUTH-002). Redis·메일 작업이 중심이라 JPA 트랜잭션을 걸지 않는다. (메일 발송 동안 DB 커넥션을 잡지 않기 위함) */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  static final Duration CODE_TTL = Duration.ofMinutes(5);
  static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
  private static final int CODE_BOUND = 1_000_000;

  private final SecureRandom random = new SecureRandom();

  private final UserRepository userRepository;
  private final EmailVerificationRepository emailVerificationRepository;
  private final MailService mailService;

  public void sendCode(String rawEmail) {
    String email = EmailNormalizer.normalize(rawEmail);
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_DUPLICATED);
    }

    String code = String.format("%06d", random.nextInt(CODE_BOUND));
    if (!emailVerificationRepository.saveCodeIfAbsent(email, code, CODE_TTL)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_CODE_ALREADY_SENT);
    }

    try {
      mailService.sendVerificationCode(email, code);
    } catch (MailException exception) {
      // 발송에 실패한 코드가 남아 있으면 5분 동안 재요청이 막히므로 지운다.
      emailVerificationRepository.deleteCode(email);
      log.error("인증 메일 발송 실패", exception);
      throw new BusinessException(AuthDomainErrorCode.MAIL_SEND_FAILED);
    }
    log.info("인증 메일 발송 완료");
  }

  public void verifyCode(String rawEmail, String code) {
    String email = EmailNormalizer.normalize(rawEmail);
    String savedCode =
        emailVerificationRepository
            .findCode(email)
            .orElseThrow(() -> new BusinessException(AuthDomainErrorCode.EMAIL_CODE_NOT_FOUND));
    if (!savedCode.equals(code)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_CODE_MISMATCH);
    }

    emailVerificationRepository.saveVerified(email, VERIFIED_TTL);
    emailVerificationRepository.deleteCode(email);
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
}
