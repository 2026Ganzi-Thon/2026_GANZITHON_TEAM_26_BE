CREATE TABLE daily_stats
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    stat_date        DATE        NOT NULL,
    pick_count       INT         NOT NULL DEFAULT 0,
    sent_count       INT         NOT NULL DEFAULT 0,
    last_sent_at     DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_stats_user_id_stat_date UNIQUE (user_id, stat_date),
    CONSTRAINT fk_daily_stats_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE warmth_map_logs
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    emotion    VARCHAR(30) NOT NULL,
    logged_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_warmth_map_logs_emotion_logged_at (emotion, logged_at),
    INDEX idx_warmth_map_logs_user_id_emotion_logged_at (user_id, emotion, logged_at),
    CONSTRAINT fk_warmth_map_logs_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
