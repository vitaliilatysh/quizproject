package ua.nure.latysh.quizzes.exceptions;

public class QuizSubmissionException extends RuntimeException {
    private final Reason reason;

    public QuizSubmissionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        NOT_FOUND,
        ALREADY_COMPLETED,
        EXPIRED,
        INVALID_ANSWER
    }
}
