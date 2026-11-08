package com.hanol.domain.auth.service;

import com.hanol.domain.auth.dto.request.LoginRequest;
import com.hanol.domain.auth.dto.request.SignupRequest;
import com.hanol.domain.auth.dto.response.LoginResponse;
import com.hanol.domain.auth.entity.TermsAgreement;
import com.hanol.domain.auth.entity.TermsType;
import com.hanol.domain.auth.exception.AuthDomainErrorCode;
import com.hanol.domain.auth.repository.TermsAgreementRepository;
import com.hanol.domain.user.entity.User;
import com.hanol.domain.user.repository.UserRepository;
import com.hanol.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입(USR-AUTH-001), 로그인(USR-AUTH-003), 로그인 잠금(USR-AUTH-004). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  static final int MAX_LOGIN_FAILURES = 5;
  static final Duration LOCK_DURATION = Duration.ofMinutes(5);

  private final UserRepository userRepository;
  private final TermsAgreementRepository termsAgreementRepository;
  private final EmailVerificationService emailVerificationService;
  private final TokenService tokenService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  @Transactional
  public void signup(SignupRequest request) {
    String email = EmailNormalizer.normalize(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_DUPLICATED);
    }
    if (!emailVerificationService.isVerified(email)) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_NOT_VERIFIED);
    }
    if (!request.agreements().containsAll(EnumSet.allOf(TermsType.class))) {
      throw new BusinessException(AuthDomainErrorCode.TERMS_NOT_AGREED);
    }

    User user =
        User.builder().email(email).password(passwordEncoder.encode(request.password())).build();
    try {
      // 중복 확인 이후 같은 이메일로 동시에 가입하면 유니크 제약(uk_users_email)에서 걸린다.
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(AuthDomainErrorCode.EMAIL_DUPLICATED);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    List<TermsAgreement> agreements =
        EnumSet.allOf(TermsType.class).stream()
            .map(type -> TermsAgreement.builder().user(user).termsType(type).agreedAt(now).build())
            .toList();
    termsAgreementRepository.saveAll(agreements);

    emailVerificationService.clearVerified(email);
    log.info("회원가입 완료: userId={}", user.getId());
  }

  /**
   * 로그인. 실패 횟수 증가는 예외를 던져도 롤백되면 안 되므로 BusinessException 은 롤백 대상에서 뺀다.
   *
   * <p>계정이 없을 때와 비밀번호가 틀렸을 때를 구분하지 않고 같은 에러(401_LOGIN_FAILED)를 준다.
   */
  @Transactional(noRollbackFor = BusinessException.class)
  public LoginResponse login(LoginRequest request) {
    String email = EmailNormalizer.normalize(request.email());
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BusinessException(AuthDomainErrorCode.LOGIN_FAILED));

    LocalDateTime now = LocalDateTime.now(clock);
    if (user.isLocked(now)) {
      throw new BusinessException(AuthDomainErrorCode.ACCOUNT_LOCKED);
    }

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      handleLoginFailure(user, now);
    }

    if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
      user.resetLoginFailure();
    }
    log.info("로그인 성공: userId={}", user.getId());
    return LoginResponse.from(tokenService.issue(user.getId()), user.isOnboardingCompleted());
  }

  private void handleLoginFailure(User user, LocalDateTime now) {
    if (user.getLockedUntil() != null) {
      // 해제 시각이 지난 잠금이 남아 있으면, 이번 실패를 1회째로 세기 위해 먼저 초기화한다.
      user.resetLoginFailure();
    }
    userRepository.increaseFailedLoginCount(
        user.getId(), MAX_LOGIN_FAILURES, now.plus(LOCK_DURATION));

    boolean lockedNow =
        userRepository.findById(user.getId()).map(u -> u.isLocked(now)).orElse(false);
    if (lockedNow) {
      log.info("로그인 잠금: userId={}", user.getId());
      throw new BusinessException(AuthDomainErrorCode.ACCOUNT_LOCKED);
    }
    throw new BusinessException(AuthDomainErrorCode.LOGIN_FAILED);
  }
}
