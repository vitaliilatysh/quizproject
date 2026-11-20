package ua.nure.latysh.quizzes.api.quiz;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.quiz.QuizAdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.quiz.QuizAdminModels.AnswerResponse;
import ua.nure.latysh.quizzes.api.quiz.QuizAdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.quiz.QuizAdminModels.QuestionResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:question_writes;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional(isolation = Isolation.REPEATABLE_READ)
class QuestionAdminPersistenceTest {
    @Autowired
    private QuestionAdminService service;

    @Autowired
    private QuestionRepository questions;

    @Autowired
    private AnswerRepository answers;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics;
    private boolean statisticsWereEnabled;

    @BeforeEach
    void populateUnrelatedQuestions() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statisticsWereEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        Quiz quiz = entityManager.getReference(Quiz.class, 1);
        for (int index = 0; index < 10; index++) {
            Question question = questions.saveAndFlush(new Question("Unrelated " + index, quiz));
            answers.saveAll(List.of(new Answer("a", true, question), new Answer("b", false, question),
                    new Answer("c", false, question), new Answer("d", false, question)));
        }
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    @AfterEach
    void restoreStatistics() {
        statistics.clear();
        statistics.setStatisticsEnabled(statisticsWereEnabled);
    }

    @Test
    void creatingAQuestionLoadsOnlyItsQuiz() {
        QuestionResponse created = service.createQuestion(1, new QuestionRequest(" New question ", List.of(
                new AnswerRequest(" First ", true), new AnswerRequest(" Second ", false),
                new AnswerRequest(" Third ", false), new AnswerRequest(" Fourth ", false))));

        assertThat(statistics.getEntityLoadCount()).as("creating one question must not load the quiz's other questions")
                .isEqualTo(1);
        assertThat(created.text()).isEqualTo("New question");
        assertThat(created.answers()).extracting(AnswerResponse::text)
                .containsExactly("First", "Second", "Third", "Fourth");
        assertThat(created.answers()).extracting(AnswerResponse::id).isSorted().allMatch(id -> id > 0);
        assertThat(reread(created.id())).isEqualTo(created);
    }

    @Test
    void editingAQuestionLoadsOnlyThatQuestionAndItsFourAnswers() {
        QuestionResponse updated = service.updateQuestion(1, new QuestionRequest(" Updated question ", List.of(
                new AnswerRequest(4, " Fourth updated ", false), new AnswerRequest(3, " Third updated ", false),
                new AnswerRequest(2, " Second updated ", false), new AnswerRequest(1, " First updated ", true))));

        assertThat(statistics.getEntityLoadCount()).as("editing one question must not load the quiz's other questions")
                .isEqualTo(5);
        assertThat(updated.text()).isEqualTo("Updated question");
        assertThat(updated.answers()).extracting(AnswerResponse::id).containsExactly(1, 2, 3, 4);
        assertThat(updated.answers()).extracting(AnswerResponse::text)
                .containsExactly("First updated", "Second updated", "Third updated", "Fourth updated");
        assertThat(reread(updated.id())).isEqualTo(updated);
    }

    @Test
    void listingIncludesQuestionsWithoutAnswersAndKeepsIdOrder() {
        Question empty = questions.saveAndFlush(new Question("No options yet", entityManager.getReference(Quiz.class, 1)));
        entityManager.clear();

        List<QuestionResponse> listed = service.questions(1);

        assertThat(listed).extracting(QuestionResponse::id).isSorted();
        assertThat(listed).filteredOn(question -> question.id() == empty.getId()).singleElement()
                .satisfies(question -> assertThat(question.answers()).isEmpty());
        assertThat(listed).allSatisfy(question -> assertThat(question.answers()).extracting(AnswerResponse::id).isSorted());
    }

    private QuestionResponse reread(int questionId) {
        entityManager.flush();
        entityManager.clear();
        return service.questions(1).stream().filter(question -> question.id() == questionId).findFirst().orElseThrow();
    }
}
