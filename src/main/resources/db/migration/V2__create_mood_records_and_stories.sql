CREATE TABLE mood_records
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    record_date DATE        NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    daily_seq   INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mood_records_user_id_record_date_daily_seq
        UNIQUE (user_id, record_date, daily_seq),
    CONSTRAINT fk_mood_records_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE mood_record_cards
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    mood_record_id BIGINT      NOT NULL,
    card_order     INT         NOT NULL,
    emotion        VARCHAR(30) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mood_record_cards_mood_record_id_card_order
        UNIQUE (mood_record_id, card_order),
    CONSTRAINT fk_mood_record_cards_mood_record_id
        FOREIGN KEY (mood_record_id) REFERENCES mood_records (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE stories
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    mood_record_id BIGINT       NOT NULL,
    content        VARCHAR(300) NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stories_mood_record_id UNIQUE (mood_record_id),
    CONSTRAINT fk_stories_mood_record_id
        FOREIGN KEY (mood_record_id) REFERENCES mood_records (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
