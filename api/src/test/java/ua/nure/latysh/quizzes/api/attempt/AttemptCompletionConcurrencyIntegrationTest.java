package ua.nure.latysh.quizzes.api.attempt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the pessimistic row lock ({@code @Lock(PESSIMISTIC_WRITE)}) taken while
 * completing an attempt actually serializes concurrent completions against a real database
 * with real row locking. A MockMvc/H2-backed unit test cannot exercise this: it only ever
 * runs one request at a time.
 */
@Testcontainers
@SpringBootTest(properties = {
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=cDctaW50ZWdyYXRpb24tc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=",
        "quiz.security.allowed-origins=https://app.example.test"
})
class AttemptCompletionConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Test
    void allowsExactlyOneOfTwoSimultaneousCompletionsToSucceed() throws Exception {
        String username = "concurrentx";
        accountService.register(new RegisterRequest(username, "Concurrent", "Student", "SecretPass1"));

        int quizId = quizRepository.findAll().stream()
                .filter(quiz -> "Test1".equals(quiz.getName()))
                .findFirst().orElseThrow().getId();
        int questionId = questionRepository.findAllByQuiz_IdOrderByIdAsc(quizId).get(0).getId();
        int answerId = answerRepository.findAllByQuestion_IdOrderByIdAsc(questionId).get(0).getId();
        int attemptId = attemptService.start(quizId, username).attemptId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> completeAttempt = () -> {
            bothReady.countDown();
            go.await();
            try {
                return attemptService.complete(attemptId, username, Set.of(answerId));
            } catch (ResourceConflictException exception) {
                return exception;
            }
        };

        try {
            Future<Object> first = pool.submit(completeAttempt);
            Future<Object> second = pool.submit(completeAttempt);
            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(AttemptCompletionResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ResourceConflictException.class::isInstance).hasSize(1);
        } finally {
            pool.shutdown();
        }

        assertThat(attemptRepository.findById(attemptId).orElseThrow().isCompleted()).isTrue();
        assertThat(resultRepository.count()).isEqualTo(1);
    }
}
