package com.hanol.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Instant NOW_INSTANT = Instant.parse("2026-11-04T03:00:00Z");
  private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZONE);
  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "Password1!";
  private static final List<TermsType> ALL_TERMS = List.of(TermsType.values());

  @Mock private UserRepository userRepository;
  @Mock private TermsAgreementRepository termsAgreementRepository;
  @Mock private EmailVerificationService emailVerificationService;
  @Mock private TokenService tokenService;
  @Mock private PasswordEncoder passwordEncoder;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            termsAgreementRepository,
            emailVerificationService,
            tokenService,
            passwordEncoder,
            Clock.fixed(NOW_INSTANT, ZONE));
  }

  private static User user(Long id, int failedLoginCount, LocalDateTime lockedUntil) {
    User user = User.builder().email(EMAIL).password("encoded").build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "failedLoginCount", failedLoginCount);
    ReflectionTestUtils.setField(user, "lockedUntil", lockedUntil);
    return user;
  }

  private static void assertErrorCode(Runnable action, AuthDomainErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Nested
  @DisplayName("회원가입")
  class Signup {

    @Test
    @DisplayName("인증 완료된 이메일이면 비밀번호를 해시해 저장하고 약관 3개 동의를 기록한 뒤 인증 표시를 지운다")
    void success() {
      given(userRepository.existsByEmail(EMAIL)).willReturn(false);
      given(emailVerificationService.isVerified(EMAIL)).willReturn(true);
      given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");

      authService.signup(new SignupRequest(" User@Example.com ", PASSWORD, ALL_TERMS));

      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).saveAndFlush(userCaptor.capture());
      User saved = userCaptor.getValue();
      assertThat(saved.getEmail()).isEqualTo(EMAIL);
      assertThat(saved.getPassword()).isEqualTo("encoded");
      assertThat(saved.isOnboardingCompleted()).isFalse();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<TermsAgreement>> termsCaptor = ArgumentCaptor.forClass(List.class);
      verify(termsAgreementRepository).saveAll(termsCaptor.capture());
      List<TermsAgreement> terms = termsCaptor.getValue();
      assertThat(terms)
          .extracting(TermsAgreement::getTermsType)
          .containsExactlyInAnyOrder(TermsType.values());
      assertThat(terms).allMatch(t -> t.getTermsVersion().equals("v1.0"));
      assertThat(terms).allMatch(t -> t.getAgreedAt().equals(NOW));

      verify(emailVerificationService).clearVerified(EMAIL);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409_EMAIL_DUPLICATED")
    void duplicatedEmail() {
      given(userRepository.existsByEmail(EMAIL)).willReturn(true);

      assertErrorCode(
          () -> authService.signup(new SignupRequest(EMAIL, PASSWORD, ALL_TERMS)),
          AuthDomainErrorCode.EMAIL_DUPLICATED);
      verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이메일 인증을 하지 않았으면 400_EMAIL_NOT_VERIFIED")
    void notVerified() {
      given(userRepository.existsByEmail(EMAIL)).willReturn(false);
      given(emailVerificationService.isVerified(EMAIL)).willReturn(false);

      assertErrorCode(
          () -> authService.signup(new SignupRequest(EMAIL, PASSWORD, ALL_TERMS)),
          AuthDomainErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("필수 약관 중 하나라도 빠지면 400_TERMS_NOT_AGREED")
    void missingTerms() {
      given(userRepository.existsByEmail(EMAIL)).willReturn(false);
      given(emailVerificationService.isVerified(EMAIL)).willReturn(true);
      List<TermsType> twoTerms = List.of(TermsType.TERMS_OF_SERVICE, TermsType.PRIVACY_POLICY);

      assertErrorCode(
          () -> authService.signup(new SignupRequest(EMAIL, PASSWORD, twoTerms)),
          AuthDomainErrorCode.TERMS_NOT_AGREED);
      verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 가입으로 저장 시점에 유니크 제약에 걸려도 409_EMAIL_DUPLICATED")
    void uniqueConstraintViolation() {
      given(userRepository.existsByEmail(EMAIL)).willReturn(false);
      given(emailVerificationService.isVerified(EMAIL)).willReturn(true);
      given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");
      given(userRepository.saveAndFlush(any()))
          .willThrow(new DataIntegrityViolationException("uk"));

      assertErrorCode(
          () -> authService.signup(new SignupRequest(EMAIL, PASSWORD, ALL_TERMS)),
          AuthDomainErrorCode.EMAIL_DUPLICATED);
      verify(emailVerificationService, never()).clearVerified(anyString());
    }
  }

  @Nested
  @DisplayName("로그인")
  class Login {

    @Test
    @DisplayName("비밀번호가 맞으면 토큰과 온보딩 완료 여부를 내려주고 실패 기록을 초기화한다")
    void success() {
      User user = user(1L, 3, null);
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(PASSWORD, "encoded")).willReturn(true);
      given(tokenService.issue(1L)).willReturn(new TokenPair("access", "refresh"));

      LoginResponse response = authService.login(new LoginRequest("USER@example.com", PASSWORD));

      assertThat(response.accessToken()).isEqualTo("access");
      assertThat(response.refreshToken()).isEqualTo("refresh");
      assertThat(response.isOnboardingCompleted()).isFalse();
      assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 401_LOGIN_FAILED")
    void unknownEmail() {
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

      assertErrorCode(
          () -> authService.login(new LoginRequest(EMAIL, PASSWORD)),
          AuthDomainErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 실패 횟수를 원자적으로 올리고 401_LOGIN_FAILED")
    void wrongPassword() {
      User user = user(1L, 1, null);
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(PASSWORD, "encoded")).willReturn(false);
      given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, 2, null)));

      assertErrorCode(
          () -> authService.login(new LoginRequest(EMAIL, PASSWORD)),
          AuthDomainErrorCode.LOGIN_FAILED);
      verify(userRepository)
          .increaseFailedLoginCount(
              1L, AuthService.MAX_LOGIN_FAILURES, NOW.plus(AuthService.LOCK_DURATION));
      verify(tokenService, never()).issue(any());
    }

    @Test
    @DisplayName("5회째 실패로 잠기면 그 요청부터 423_ACCOUNT_LOCKED")
    void lockOnFifthFailure() {
      User user = user(1L, 4, null);
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(PASSWORD, "encoded")).willReturn(false);
      given(userRepository.findById(1L))
          .willReturn(Optional.of(user(1L, 5, NOW.plus(AuthService.LOCK_DURATION))));

      assertErrorCode(
          () -> authService.login(new LoginRequest(EMAIL, PASSWORD)),
          AuthDomainErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("해제 시각 전에는 비밀번호가 맞아도 423_ACCOUNT_LOCKED")
    void lockedBeforeUnlockTime() {
      User user = user(1L, 5, NOW.plusMinutes(1));
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

      assertErrorCode(
          () -> authService.login(new LoginRequest(EMAIL, PASSWORD)),
          AuthDomainErrorCode.ACCOUNT_LOCKED);
      verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("해제 시각이 지나면 잠금이 풀려 로그인되고 실패 기록이 초기화된다")
    void unlockedAfterUnlockTime() {
      User user = user(1L, 5, NOW.minusSeconds(1));
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(PASSWORD, "encoded")).willReturn(true);
      given(tokenService.issue(1L)).willReturn(new TokenPair("access", "refresh"));

      authService.login(new LoginRequest(EMAIL, PASSWORD));

      assertThat(user.getFailedLoginCount()).isZero();
      assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("해제 시각이 지난 뒤 다시 틀리면 실패 횟수를 초기화한 뒤 1회째로 센다")
    void failAfterUnlockTimeCountsFromOne() {
      User user = user(1L, 5, NOW.minusSeconds(1));
      given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
      given(passwordEncoder.matches(PASSWORD, "encoded")).willReturn(false);
      given(userRepository.findById(1L)).willReturn(Optional.of(user(1L, 1, null)));

      assertErrorCode(
          () -> authService.login(new LoginRequest(EMAIL, PASSWORD)),
          AuthDomainErrorCode.LOGIN_FAILED);
      assertThat(user.getFailedLoginCount()).isZero();
      assertThat(user.getLockedUntil()).isNull();
    }
  }
}
