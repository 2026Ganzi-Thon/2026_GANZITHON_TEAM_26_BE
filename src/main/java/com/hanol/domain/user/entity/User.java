package com.hanol.domain.user.entity;

import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password", nullable = false)
  private String password;

  @Column(name = "is_onboarding_completed", nullable = false)
  private boolean onboardingCompleted;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private LocalDateTime lockedUntil;

  @Builder
  private User(String email, String password) {
    this.email = email;
    this.password = password;
    this.onboardingCompleted = false;
    this.failedLoginCount = 0;
  }

  /** 해제 시각 전이면 잠금 상태. */
  public boolean isLocked(LocalDateTime now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /** 잠금이 풀렸거나(해제 시각 경과) 로그인에 성공하면 실패 기록을 초기화한다. */
  public void resetLoginFailure() {
    this.failedLoginCount = 0;
    this.lockedUntil = null;
  }
}
