INSERT INTO roles VALUES (1, 'admin'), (2, 'student');
INSERT INTO statuses VALUES (1, 'active'), (2, 'blocked');
INSERT INTO users VALUES
  (1, 'student', 'secret123', 'Student', 'User', TIMESTAMP '2025-01-01 09:00:00', NULL, 1, 2),
  (2, 'blocked', 'secret123', 'Blocked', 'User', TIMESTAMP '2025-01-02 09:00:00', NULL, 2, 2),
  (3, 'empty', 'secret123', 'Empty', 'User', TIMESTAMP '2025-01-03 09:00:00', NULL, 1, 2),
  (4, 'encoded', 'placeholder', 'Encoded', 'User', TIMESTAMP '2025-01-04 09:00:00', NULL, 1, 2),
  (5, 'admin', 'secret123', 'Admin', 'User', TIMESTAMP '2025-01-05 09:00:00', NULL, 1, 1),
  (6, 'apiuser', 'secret123', 'API', 'User', TIMESTAMP '2025-01-06 09:00:00', NULL, 1, 2),
  (7, 'orphan', 'secret123', 'Orphan', 'User', TIMESTAMP '2025-01-07 09:00:00', NULL, 1, 2);
ALTER TABLE users ALTER COLUMN id RESTART WITH 8;
INSERT INTO subjects VALUES (1, 'Java Basics'), (2, 'Collections');
INSERT INTO levels VALUES (1, 'low'), (2, 'medium');
INSERT INTO quizzes VALUES
  (1, 'Java syntax', 5, 1, 1),
  (2, 'Lists', 10, 2, 2);
INSERT INTO questions VALUES
  (1, 'Question 1', 1),
  (2, 'Question 2', 1);
INSERT INTO answers VALUES
  (1, 'Answer 1.1', TRUE, 1),
  (2, 'Answer 1.2', FALSE, 1),
  (3, 'Answer 1.3', FALSE, 1),
  (4, 'Answer 1.4', FALSE, 1),
  (5, 'Answer 2.1', TRUE, 2),
  (6, 'Answer 2.2', TRUE, 2),
  (7, 'Answer 2.3', FALSE, 2),
  (8, 'Answer 2.4', FALSE, 2);
ALTER TABLE subjects ALTER COLUMN id RESTART WITH 3;
ALTER TABLE quizzes ALTER COLUMN id RESTART WITH 3;
ALTER TABLE questions ALTER COLUMN id RESTART WITH 3;
ALTER TABLE answers ALTER COLUMN id RESTART WITH 9;
INSERT INTO attempts VALUES
  (1, 80, TIMESTAMP '2026-08-12 10:10:30', TIMESTAMP '2026-08-12 10:15:30',
   TIMESTAMP '2026-08-12 10:15:30', TRUE, 1, 1),
  (2, 0, TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2020-01-01 00:05:00',
   NULL, FALSE, 1, 6),
  (3, 0, TIMESTAMP '2099-01-01 00:00:00', TIMESTAMP '2099-01-01 00:05:00',
   NULL, FALSE, 2, 6);
ALTER TABLE attempts ALTER COLUMN id RESTART WITH 4;

