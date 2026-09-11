-- What the reader was shown, frozen at the moment their attempt started.
--
-- Scoring used to read the quiz as it stood when the attempt was completed, so
-- an administrator editing a quiz reached into every attempt already open on
-- it. Two ways, both measured against the seeded contract database:
--
--   a question added mid-attempt   the reader answered every question they had
--                                  been shown, correctly, and scored 66 — the
--                                  new question counted against them.
--   a question deleted mid-attempt their submission carried an answer id that
--                                  no longer belonged to the quiz, so the whole
--                                  attempt was refused with a 400 and could not
--                                  be finished at all.
--
-- The text is copied, not referenced, and that is the point: a row here has to
-- survive the question it was copied from. So there is deliberately no foreign
-- key to questions or answers — one would either cascade this snapshot away
-- with the row it describes or block the administrator from deleting it. The
-- only foreign key is to the attempt, which is what this belongs to.
CREATE TABLE attempt_questions
(
  id            INTEGER      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id    INTEGER      NOT NULL,
  question_id   INTEGER      NOT NULL,
  question_text VARCHAR(250) NOT NULL,
  answer_id     INTEGER      NOT NULL,
  answer_text   VARCHAR(50)  NOT NULL,
  correct       BOOLEAN      NOT NULL,
  CONSTRAINT uq_attempt_questions_attempt_answer UNIQUE (attempt_id, answer_id),
  CONSTRAINT fk_attempt_questions_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id)
    ON DELETE CASCADE ON UPDATE RESTRICT
);

-- Attempts that were already open when this table appeared. A completed attempt
-- needs nothing: its score is already stored. An open one is copied from the
-- quiz as it stands now, which is the closest thing to what its reader was
-- shown that this migration can still see.
INSERT INTO attempt_questions (attempt_id, question_id, question_text, answer_id, answer_text, correct)
SELECT attempt.id, question.id, question.question, answer.id, answer.answer, COALESCE(answer.correct, FALSE)
FROM attempts attempt
JOIN questions question ON question.quiz_id = attempt.quiz_id
JOIN answers answer ON answer.question_id = question.id
WHERE attempt.completed = FALSE;
