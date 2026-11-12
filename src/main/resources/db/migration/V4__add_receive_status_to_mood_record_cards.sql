ALTER TABLE mood_record_cards
    ADD COLUMN receive_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RECEIVED' AFTER emotion;
