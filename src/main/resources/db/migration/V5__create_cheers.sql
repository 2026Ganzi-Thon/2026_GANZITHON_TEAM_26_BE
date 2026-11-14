CREATE TABLE cheers
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    sender_hash    VARCHAR(64) NOT NULL,
    target_emotion VARCHAR(30) NOT NULL,
    content        VARCHAR(50) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_cheers_target_emotion_status_created_at (target_emotion, status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
