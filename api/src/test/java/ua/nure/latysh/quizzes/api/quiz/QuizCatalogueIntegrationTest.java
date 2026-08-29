package ua.nure.latysh.quizzes.api.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import ua.nure.latysh.quizzes.api.domain.SubjectRepository;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract tests cover this query on H2, which is neither where it runs in
 * production nor what shaped it. Two details are MySQL-specific: an empty
 * {@code IN ()} is a syntax error there, and {@code LIKE ... ESCAPE} has to be
 * honoured for a search term containing a wildcard to stay literal. Both are
 * asserted here against a real MySQL running the production Flyway migrations.
 */
@Testcontainers
@SpringBootTest(properties = {
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=cXVpei1zZWFyY2gtaW50ZWdyYXRpb24tc2VjcmV0LTMyLWJ5dGVz",
        "quiz.security.allowed-origins=https://app.example.test"
})
class QuizCatalogueIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    @Autowired
    private QuizQueryService quizQueryService;

    @Autowired
    private SubjectRepository subjectRepository;

    @Test
    void listsEverythingWhenNoFilterIsGiven() {
        // Exercises the ANY_COMPLEXITY placeholder against MySQL. Passing a
        // genuinely empty collection here renders IN () and fails outright.
        long total = quizQueryService.findAll(null, null, PageRequest.of(0, 50)).getTotalElements();
        assertThat(total).isPositive();

        assertThat(quizQueryService.findAll("", List.of(), PageRequest.of(0, 50)).getTotalElements())
                .isEqualTo(total);
        assertThat(quizQueryService.findAll("  ", List.of("", "   "), PageRequest.of(0, 50))
                .getTotalElements())
                .isEqualTo(total);
    }

    @Test
    void countsOnlySubjectsThatCarryAQuiz() {
        // The production seed has four subjects but only three of them are used
        // by a quiz — 'Java Array' has none. The home page derived this figure
        // from the quiz list, so counting the subjects table instead would show
        // a larger number than the catalogue actually offers to browse.
        QuizCatalogueSummary summary = quizQueryService.summary();

        assertThat(summary.totalQuizzes())
                .isEqualTo(quizQueryService.findAll(null, null, PageRequest.of(0, 100))
                        .getTotalElements());
        assertThat(summary.totalSubjects())
                .isPositive()
                .isLessThan(subjectRepository.count());
    }

    @Test
    void narrowsByStoredLevelLabelOnMySql() {
        long all = quizQueryService.findAll(null, null, PageRequest.of(0, 50)).getTotalElements();
        long low = quizQueryService.findAll(null, List.of("low"), PageRequest.of(0, 50))
                .getTotalElements();
        long lowOrMedium = quizQueryService
                .findAll(null, List.of("LOW", "medium"), PageRequest.of(0, 50))
                .getTotalElements();

        assertThat(low).isPositive().isLessThanOrEqualTo(all);
        assertThat(lowOrMedium).isGreaterThanOrEqualTo(low);
        assertThat(quizQueryService.findAll(null, List.of("no-such-level"), PageRequest.of(0, 50))
                .getTotalElements())
                .isZero();
    }

    @Test
    void keepsWildcardsLiteralOnMySql() {
        long all = quizQueryService.findAll(null, null, PageRequest.of(0, 50)).getTotalElements();
        assertThat(all).isGreaterThan(1);

        // No seeded quiz name or subject contains these characters. Without the
        // ESCAPE clause the pattern collapses to "match anything" and these
        // return every row instead of none.
        assertThat(quizQueryService.findAll("%", null, PageRequest.of(0, 50)).getTotalElements())
                .isZero();
        assertThat(quizQueryService.findAll("_", null, PageRequest.of(0, 50)).getTotalElements())
                .isZero();
    }

    @Test
    void matchesNameAndSubjectCaseInsensitivelyOnMySql() {
        List<QuizResponse> everything =
                quizQueryService.findAll(null, null, PageRequest.of(0, 50)).getContent();
        QuizResponse sample = everything.getFirst();

        assertThat(quizQueryService
                .findAll(sample.name().toUpperCase(Locale.ROOT), null, PageRequest.of(0, 50))
                .getContent())
                .extracting(QuizResponse::id)
                .contains(sample.id());

        assertThat(quizQueryService
                .findAll(sample.subject().toLowerCase(Locale.ROOT), null, PageRequest.of(0, 50))
                .getContent())
                .extracting(QuizResponse::id)
                .contains(sample.id());
    }
}
