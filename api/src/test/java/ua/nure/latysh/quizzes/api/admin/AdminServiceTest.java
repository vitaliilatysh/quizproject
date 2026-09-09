package ua.nure.latysh.quizzes.api.admin;

import org.junit.jupiter.api.Test;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.domain.Answer;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.LevelRepository;
import ua.nure.latysh.quizzes.api.domain.Question;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.domain.Role;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.SubjectRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The administration paths a correctly seeded database cannot reach.
 *
 * The contract test drives this service through MockMvc against the schema the
 * migrations build, which is the right way to test what it does. What it cannot
 * do is take the branches that only open when the data is not what the schema
 * promises — a status row that is gone, or a question that vanishes between
 * being written and being read back. Those decide whether the API answers with
 * a diagnosable error or a NullPointerException, so they are tested here with
 * the repositories mocked to produce exactly that state.
 */
class AdminServiceTest {
    private final SubjectRepository subjectRepository = mock(SubjectRepository.class);
    private final LevelRepository levelRepository = mock(LevelRepository.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final AnswerRepository answerRepository = mock(AnswerRepository.class);
    private final AttemptRepository attemptRepository = mock(AttemptRepository.class);
    private final ResultRepository resultRepository = mock(ResultRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StatusRepository statusRepository = mock(StatusRepository.class);

    private final AdminService service = new AdminService(
            subjectRepository, levelRepository, quizRepository, questionRepository,
            answerRepository, attemptRepository, resultRepository, userRepository, statusRepository);

    private static final QuestionRequest QUESTION = new QuestionRequest("Що таке JVM?", List.of(
            new AnswerRequest("Віртуальна машина", true),
            new AnswerRequest("Компілятор", false),
            new AnswerRequest("Редактор", false),
            new AnswerRequest("Профайлер", false)));

    private static UserAccount user(String login) {
        var account = new UserAccount();
        account.setLogin(login);
        account.setStatus(new Status());
        account.setRole(new Role());
        return account;
    }

    /**
     * Stands in for the identity column.
     *
     * A generated id is assigned by the database on flush, so with the
     * repositories mocked every entity here would keep a null one — and the
     * service unboxes those ids into its messages. Setting the field is the
     * smallest way to make a mocked save behave like a real one; the
     * alternative is a test that proves only that null does not unbox.
     */
    private static <T> T withId(T entity, int id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void refusesAStatusChangeToAStatusTheDatabaseDoesNotHave() {
        when(userRepository.findById(3)).thenReturn(Optional.of(user("olena")));
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.empty());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.updateUserStatus(3, "ACTIVE", "admin"));

        assertEquals("Status 'active' is not configured", failure.getMessage());
    }

    // A question written and then not found when it is read back. Only a delete
    // committed between the two statements produces this, which is why the
    // contract test cannot; what it must not produce is an empty Optional
    // dereferenced somewhere further up.
    @Test
    void reportsAQuestionThatDisappearedBetweenWritingItAndReadingItBack() {
        when(quizRepository.findById(7)).thenReturn(Optional.of(withId(new Quiz(), 7)));
        when(questionRepository.saveAndFlush(any(Question.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 11));
        when(questionRepository.findAllByQuiz_IdOrderByIdAsc(anyInt())).thenReturn(List.of());
        when(answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(anyInt())).thenReturn(List.of());

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.createQuestion(7, QUESTION));

        assertEquals("Question 11 was not found", failure.getMessage());
    }

    @Test
    void reportsAnUpdatedQuestionThatIsNoLongerOnItsQuiz() {
        var quiz = withId(new Quiz(), 7);
        var question = withId(new Question("Що таке JVM?", quiz), 11);
        when(questionRepository.findById(7)).thenReturn(Optional.of(question));
        when(answerRepository.findAllByQuestion_IdOrderByIdAsc(7)).thenReturn(List.of(
                new Answer("a", true, question), new Answer("b", false, question),
                new Answer("c", false, question), new Answer("d", false, question)));
        // The quiz still lists a question, but not this one — so the filter runs
        // and matches nothing, rather than never running at all.
        when(questionRepository.findAllByQuiz_IdOrderByIdAsc(anyInt())).thenReturn(List.of(question));
        when(answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(anyInt())).thenReturn(List.of());

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.updateQuestion(7, QUESTION));

        assertEquals("Question 7 was not found", failure.getMessage());
    }
}
