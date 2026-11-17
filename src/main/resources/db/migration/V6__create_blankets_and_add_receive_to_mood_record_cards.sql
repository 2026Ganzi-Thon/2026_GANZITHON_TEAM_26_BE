CREATE TABLE blankets
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_blankets_user_id_completed_at (user_id, completed_at),
    CONSTRAINT fk_blankets_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE blanket_pieces
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    blanket_id  BIGINT      NOT NULL,
    slot_index  INT         NOT NULL,
    cheer_id    BIGINT      NULL,
    content     VARCHAR(50) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_blanket_pieces_blanket_id_slot_index UNIQUE (blanket_id, slot_index),
    INDEX idx_blanket_pieces_cheer_id (cheer_id),
    CONSTRAINT fk_blanket_pieces_blanket_id FOREIGN KEY (blanket_id) REFERENCES blankets (id),
    CONSTRAINT fk_blanket_pieces_cheer_id FOREIGN KEY (cheer_id) REFERENCES cheers (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE mood_record_cards
    ADD COLUMN received_message VARCHAR(50) NULL AFTER receive_status,
    ADD COLUMN blanket_piece_id BIGINT      NULL AFTER received_message,
    ADD CONSTRAINT fk_mood_record_cards_blanket_piece_id
        FOREIGN KEY (blanket_piece_id) REFERENCES blanket_pieces (id);
