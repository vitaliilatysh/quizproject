INSERT INTO roles VALUES (1, 'admin'), (2, 'student');
INSERT INTO statuses VALUES (1, 'active'), (2, 'blocked');
INSERT INTO users VALUES
  (1, 'student', 'secret123', 1, 2),
  (2, 'blocked', 'secret123', 2, 2),
  (3, 'empty', 'secret123', 1, 2),
  (4, 'encoded', 'placeholder', 1, 2);
INSERT INTO subjects VALUES (1, 'Java Basics'), (2, 'Collections');
INSERT INTO levels VALUES (1, 'low'), (2, 'medium');
INSERT INTO quizzes VALUES
  (1, 'Java syntax', 5, 1, 1),
  (2, 'Lists', 10, 2, 2);
INSERT INTO questions VALUES
  (1, 'Question 1', 1),
  (2, 'Question 2', 1);
INSERT INTO attempts VALUES
  (1, 80, TIMESTAMP '2026-08-12 10:15:30', TRUE, 1, 1),
  (2, 20, NULL, FALSE, 2, 1);

