CREATE TABLE roles
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(10) NOT NULL UNIQUE
);
INSERT INTO roles VALUES (1, 'admin'), (2, 'student');

CREATE TABLE statuses
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(15) NOT NULL UNIQUE
);

INSERT INTO statuses VALUES (1, 'active'), (2, 'blocked');

CREATE TABLE users
(
  id            INTEGER      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login         VARCHAR(15)  NOT NULL UNIQUE,
  password      VARCHAR(15)  NOT NULL,
  first_name    VARCHAR(20)  NOT NULL,
  last_name     VARCHAR(20)  NOT NULL,
  register_date DATETIME     NOT NULL,
  login_date    DATETIME,
  status_id     INTEGER      NOT NULL,
  role_id       INTEGER      NOT NULL,
  FOREIGN KEY (status_id) REFERENCES statuses (id),
  FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE subjects
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(25) NOT NULL UNIQUE
);

INSERT INTO subjects (name) VALUES ('Java Basics'), ('Java Array'), ('Collection'), ('IO');

CREATE TABLE levels
(
  id    INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  level VARCHAR(10) NOT NULL
);

INSERT INTO levels VALUES (1, 'low'), (2, 'medium'), (3, 'high'), (4, 'advanced');

CREATE TABLE quizzes
(
  id           INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(50) NOT NULL UNIQUE,
  time_to_pass INTEGER     NOT NULL,
  level_id     INTEGER     NOT NULL,
  subject_id   INTEGER     NOT NULL,
  FOREIGN KEY (level_id) REFERENCES levels (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

INSERT INTO quizzes (name, time_to_pass, level_id, subject_id) VALUES
  ('Test1', 5, 1, 1),
  ('Test2', 3, 1, 1),
  ('Test3', 3, 2, 3),
  ('Test4', 1, 2, 4);

CREATE TABLE questions
(
  id       INTEGER      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question VARCHAR(250) NOT NULL,
  quiz_id  INTEGER      NOT NULL,
  FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

INSERT INTO questions (question, quiz_id) VALUES
  ('Question1', 1), ('Question2', 1), ('Question3', 1), ('Question1', 2), ('Question2', 2);

CREATE TABLE answers
(
  id          INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer      VARCHAR(50) NOT NULL,
  correct     BOOLEAN,
  question_id INTEGER     NOT NULL,
  FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

INSERT INTO answers (answer, correct, question_id) VALUES
  ('correct', TRUE, 1), ('no', FALSE, 1), ('correct', TRUE, 1), ('correct', TRUE, 1),
  ('correct', TRUE, 2), ('correct', TRUE, 2), ('no', FALSE, 2), ('no', FALSE, 2),
  ('correct', TRUE, 3), ('no', FALSE, 3), ('no', FALSE, 3), ('no', FALSE, 3),
  ('correct', TRUE, 4), ('correct', TRUE, 4), ('no', FALSE, 4), ('no', FALSE, 4),
  ('correct', TRUE, 5), ('correct', TRUE, 5), ('correct', TRUE, 5), ('no', FALSE, 5);

CREATE TABLE attempts
(
  id       INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  score    INTEGER NOT NULL DEFAULT 0,
  end_time DATETIME,
  quiz_id  INTEGER NOT NULL,
  user_id  INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
  FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE results
(
  id         INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer_id  INTEGER NOT NULL,
  attempt_id INTEGER NOT NULL,
  FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE ON UPDATE RESTRICT
);
