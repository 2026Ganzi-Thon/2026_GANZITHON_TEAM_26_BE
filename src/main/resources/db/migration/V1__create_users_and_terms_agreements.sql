CREATE TABLE users
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    email                   VARCHAR(255) NOT NULL,
    password                VARCHAR(255) NOT NULL,
    is_onboarding_completed BIT(1)       NOT NULL DEFAULT b'0',
    failed_login_count      INT          NOT NULL DEFAULT 0,
    locked_until            DATETIME(6)  NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE terms_agreements
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    terms_type    VARCHAR(30) NOT NULL,
    terms_version VARCHAR(20) NOT NULL,
    agreed_at     DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_agreements_user_id_terms_type_terms_version
        UNIQUE (user_id, terms_type, terms_version),
    CONSTRAINT fk_terms_agreements_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
