CREATE TABLE send_restrictions
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    sender_hash      VARCHAR(64) NOT NULL,
    restricted_until DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_send_restrictions_sender_hash UNIQUE (sender_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
