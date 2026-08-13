package ua.nure.latysh.quizzes.servlets;

final class BadRequestException extends Exception {

    private static final long serialVersionUID = 1L;

    BadRequestException(String message) {
        super(message);
    }
}
