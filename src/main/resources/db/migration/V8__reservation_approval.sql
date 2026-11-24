-- Reservations now wait for a manager's approval. These columns record who decided and why.
ALTER TABLE reservations ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE reservations ADD COLUMN reviewed_at DATETIME(6) NULL;
ALTER TABLE reservations ADD COLUMN review_note VARCHAR(255) NULL;
ALTER TABLE reservations ADD CONSTRAINT fk_reservations_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id);
CREATE INDEX idx_reservations_user_start ON reservations (user_id, start_time);
