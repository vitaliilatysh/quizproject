DROP SCHEMA IF EXISTS tests_db;
CREATE SCHEMA IF NOT EXISTS tests_db
  CHARACTER SET utf8
  COLLATE utf8_unicode_ci;
USE tests_db;

CREATE TABLE roles
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(10) NOT NULL UNIQUE
);

INSERT INTO roles
VALUES (1, 'admin');
INSERT INTO roles
VALUES (2, 'student');

CREATE TABLE statuses
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(15) NOT NULL UNIQUE
);

INSERT INTO statuses
VALUES (1, 'active');
INSERT INTO statuses
VALUES (2, 'blocked');

CREATE TABLE users
(
  id            INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login         VARCHAR(15) NOT NULL UNIQUE,
  password      VARCHAR(255) NOT NULL,
  first_name    VARCHAR(20) NOT NULL,
  last_name     VARCHAR(20) NOT NULL,
  register_date DATETIME    NOT NULL,
  login_date    DATETIME,
  status_id     INTEGER     NOT NULL,
  role_id       INTEGER     NOT NULL,
  FOREIGN KEY (status_id) REFERENCES statuses (id),
  FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Register the first user through the application, then promote it explicitly:
-- UPDATE users SET role_id = 1 WHERE login = '<initial-admin-login>';

CREATE TABLE subjects
(
  id   INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(25) NOT NULL UNIQUE
);

INSERT INTO subjects (name)
VALUES ('Java Basics');
INSERT INTO subjects (name)
VALUES ('Java Array');
INSERT INTO subjects (name)
VALUES ('Collection');
INSERT INTO subjects (name)
VALUES ('IO');

CREATE TABLE levels
(
  id    INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  level VARCHAR(10) NOT NULL
);

INSERT INTO levels
VALUES (1, 'low');
INSERT INTO levels
VALUES (2, 'medium');
INSERT INTO levels
VALUES (3, 'high');
INSERT INTO levels
VALUES (4, 'advanced');

CREATE TABLE quizzes
(
  id           INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(50) NOT NULL UNIQUE,
  time_to_pass INTEGER     NOT NULL,
  level_id     INTEGER     NOT NULL,
  subject_id   INTEGER     NOT NULL,
  FOREIGN KEY (level_id)
    REFERENCES levels (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT,
  FOREIGN KEY (subject_id)
    REFERENCES subjects (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
);

INSERT INTO quizzes (name, time_to_pass, level_id, subject_id)
VALUES ('Test1', 5, 1, 1);
INSERT INTO quizzes (name, time_to_pass, level_id, subject_id)
VALUES ('Test2', 3, 1, 1);
INSERT INTO quizzes (name, time_to_pass, level_id, subject_id)
VALUES ('Test3', 3, 2, 3);
INSERT INTO quizzes (name, time_to_pass, level_id, subject_id)
VALUES ('Test4', 1, 2, 4);

CREATE TABLE questions
(
  id       INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question VARCHAR(250) NOT NULL,
  quiz_id  INTEGER     NOT NULL,
  FOREIGN KEY (quiz_id)
    REFERENCES quizzes (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
);

INSERT INTO questions (question, quiz_id)
VALUES ('Question1', 1);
INSERT INTO questions (question, quiz_id)
VALUES ('Question2', 1);
INSERT INTO questions (question, quiz_id)
VALUES ('Question3', 1);
INSERT INTO questions (question, quiz_id)
VALUES ('Question1', 2);
INSERT INTO questions (question, quiz_id)
VALUES ('Question2', 2);

CREATE TABLE answers
(
  id          INTEGER     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer      VARCHAR(50) NOT NULL,
  correct     BOOLEAN,
  question_id INTEGER     NOT NULL,
  FOREIGN KEY (question_id)
    REFERENCES questions (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
);

INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 1);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 1);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 1);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 1);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 2);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 2);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 2);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 2);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 3);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 3);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 3);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 3);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 4);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 4);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 4);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 4);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 5);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 5);
INSERT INTO answers (answer, correct, question_id)
VALUES ('correct', true, 5);
INSERT INTO answers (answer, correct, question_id)
VALUES ('no', false, 5);

CREATE TABLE attempts
(
  id         INTEGER  NOT NULL AUTO_INCREMENT PRIMARY KEY,
  score      INTEGER  NOT NULL DEFAULT 0,
  start_time DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  end_time   DATETIME,
  completed  BOOLEAN  NOT NULL DEFAULT FALSE,
  quiz_id    INTEGER  NOT NULL,
  user_id    INTEGER  NOT NULL,
  FOREIGN KEY (user_id)
    REFERENCES users (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT,
  FOREIGN KEY (quiz_id)
    REFERENCES quizzes (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
);

CREATE TABLE results
(
  id         INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  answer_id  INTEGER NOT NULL,
  attempt_id INTEGER NOT NULL,
  CONSTRAINT uq_results_attempt_answer UNIQUE (attempt_id, answer_id),
  FOREIGN KEY (answer_id)
    REFERENCES answers (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT,
  FOREIGN KEY (attempt_id)
    REFERENCES attempts (id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
);


