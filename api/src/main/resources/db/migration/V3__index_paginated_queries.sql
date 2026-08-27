CREATE INDEX idx_attempts_user_completed_end_time
  ON attempts (user_id, completed, end_time DESC, id DESC);

CREATE INDEX idx_attempts_completed_end_time
  ON attempts (completed, end_time DESC, id DESC);
