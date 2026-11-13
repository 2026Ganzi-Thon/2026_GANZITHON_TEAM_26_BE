package com.hanol.domain.auth.entity;

import com.hanol.domain.user.entity.User;
import com.hanol.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "terms_agreements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_terms_agreements_user_id_terms_type_terms_version",
            columnNames = {"user_id", "terms_type", "terms_version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "terms_type", nullable = false, length = 30)
  private TermsType termsType;

  @Column(name = "terms_version", nullable = false, length = 20)
  private String termsVersion;

  @Column(name = "agreed_at", nullable = false)
  private LocalDateTime agreedAt;

  @Builder
  private TermsAgreement(User user, TermsType termsType, LocalDateTime agreedAt) {
    this.user = user;
    this.termsType = termsType;
    this.termsVersion = termsType.getVersion();
    this.agreedAt = agreedAt;
  }
}
