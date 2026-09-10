package ua.nure.latysh.quizzes.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One answer option, as one attempt was shown it.
 *
 * A copy rather than a reference. The question and answer ids are kept so a
 * submission can still be matched against them, but the text and the
 * correctness flag are the values that were true when the attempt started, and
 * they stay that way however the quiz is edited afterwards. That is the whole
 * purpose: an administrator editing a quiz used to reach into every attempt
 * already open on it, adding questions the reader was never shown and removing
 * answers they had already ticked.
 *
 * <p>There is no association to {@link Question} or {@link Answer} on purpose.
 * A row here has to outlive the row it was copied from — the deleted-question
 * case is exactly the one that used to lose a reader's whole attempt.
 */
@Entity
@Table(name = "attempt_questions")
public class AttemptQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "question_id", nullable = false)
    private int questionId;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "answer_id", nullable = false)
    private int answerId;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(nullable = false)
    private boolean correct;

    public AttemptQuestion() {
    }

    public AttemptQuestion(
            Attempt attempt, int questionId, String questionText,
            int answerId, String answerText, boolean correct) {
        this.attempt = attempt;
        this.questionId = questionId;
        this.questionText = questionText;
        this.answerId = answerId;
        this.answerText = answerText;
        this.correct = correct;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public int getAnswerId() {
        return answerId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public boolean isCorrect() {
        return correct;
    }
}
