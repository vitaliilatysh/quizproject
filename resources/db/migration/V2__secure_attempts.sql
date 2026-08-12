ALTER TABLE users
  MODIFY password VARCHAR(255) NOT NULL;

ALTER TABLE attempts
  ADD COLUMN start_time DATETIME NULL AFTER score,
  ADD COLUMN expires_at DATETIME NULL AFTER start_time,
  ADD COLUMN completed BOOLEAN NOT NULL DEFAULT FALSE AFTER end_time;

UPDATE attempts
SET start_time = COALESCE(end_time, CURRENT_TIMESTAMP),
    expires_at = COALESCE(end_time, CURRENT_TIMESTAMP),
    completed = end_time IS NOT NULL
WHERE start_time IS NULL OR expires_at IS NULL;

ALTER TABLE attempts
  MODIFY start_time DATETIME NOT NULL,
  MODIFY expires_at DATETIME NOT NULL;

DELETE invalid_result
FROM results invalid_result
LEFT JOIN answers answer ON answer.id = invalid_result.answer_id
WHERE answer.id IS NULL;

DELETE duplicate_result
FROM results duplicate_result
JOIN results original_result
  ON duplicate_result.attempt_id = original_result.attempt_id
 AND duplicate_result.answer_id = original_result.answer_id
 AND duplicate_result.id > original_result.id;

ALTER TABLE results
  ADD CONSTRAINT uq_results_attempt_answer UNIQUE (attempt_id, answer_id),
  ADD CONSTRAINT fk_results_answer FOREIGN KEY (answer_id) REFERENCES answers (id)
    ON DELETE CASCADE ON UPDATE RESTRICT;
