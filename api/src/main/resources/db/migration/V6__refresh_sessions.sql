CREATE TABLE refresh_sessions
(
  id           CHAR(36)     NOT NULL PRIMARY KEY,
  user_id      INTEGER      NOT NULL,
  token_hash   CHAR(64)     NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  last_used_at DATETIME(6)  NULL,
  expires_at   DATETIME(6)  NOT NULL,
  revoked_at   DATETIME(6)  NULL,
  CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE CASCADE ON UPDATE RESTRICT,
  INDEX idx_refresh_sessions_user_active (user_id, revoked_at, expires_at)
);
