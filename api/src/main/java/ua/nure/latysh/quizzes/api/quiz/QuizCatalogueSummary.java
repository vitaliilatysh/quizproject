package ua.nure.latysh.quizzes.api.quiz;

/**
 * Catalogue-wide totals that cannot be derived from a single page.
 *
 * <p>{@code totalSubjects} counts subjects that carry at least one quiz, which
 * is what the catalogue actually offers to browse — not every row in the
 * subjects table.
 */
public record QuizCatalogueSummary(long totalQuizzes, long totalSubjects) {
}
