CREATE TABLE reports
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    cheer_id    BIGINT      NOT NULL,
    sender_hash VARCHAR(64) NOT NULL,
    reported_at DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reports_cheer_id UNIQUE (cheer_id),
    CONSTRAINT fk_reports_cheer_id FOREIGN KEY (cheer_id) REFERENCES cheers (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
