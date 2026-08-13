package ua.nure.latysh.quizzes.services;

import org.junit.Test;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.QuizRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestionAggregateServiceTest {

    @Test
    public void createAndUpdateUseValidatedQuestionAggregate() {
        Fixture fixture = new Fixture();
        Quiz quiz = new Quiz();
        quiz.setId(7);
        Question persisted = question(9, 7);
        when(fixture.quizRepository.findById(7)).thenReturn(Optional.of(quiz));
        when(fixture.questionRepository.createWithAnswers(
                org.mockito.ArgumentMatchers.any(Question.class),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(persisted);
        when(fixture.questionRepository.findById(9)).thenReturn(Optional.of(persisted));

        assertEquals(persisted, fixture.service.createQuestion("Question", 7, answers(false, true)));
        fixture.service.updateQuestion(9, "Changed", answers(true, true));

        verify(fixture.questionRepository).updateWithAnswers(
                org.mockito.ArgumentMatchers.argThat(question -> "Changed".equals(question.getQuestion())),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void detailsLoadExactlyFourPersistedAnswers() {
        Fixture fixture = new Fixture();
        Question question = question(9, 7);
        List<Answer> answers = answers(true, true);
        when(fixture.questionRepository.findById(9)).thenReturn(Optional.of(question));
        when(fixture.answerRepository.findAllByQuestionId(9)).thenReturn(answers);

        QuestionService.QuestionDetails details = fixture.service.getQuestionDetails(9);

        assertEquals(question, details.question());
        assertEquals(answers, details.answers());
    }

    @Test
    public void invalidAnswerSetsAreRejectedBeforePersistence() {
        Fixture fixture = new Fixture();
        Quiz quiz = new Quiz();
        quiz.setId(7);
        Question question = question(9, 7);
        when(fixture.quizRepository.findById(7)).thenReturn(Optional.of(quiz));
        when(fixture.questionRepository.findById(9)).thenReturn(Optional.of(question));
        when(fixture.answerRepository.findAllByQuestionId(9)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createQuestion("Question", 7, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.createQuestion("Question", 7, answers(false, false)));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.updateQuestion(9, "Question", answers(false, true)));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.getQuestionDetails(9));
    }

    @Test
    public void legacyConstructorFailsClearlyWhenDetailsNeedAnswers() {
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        QuizRepository quizRepository = mock(QuizRepository.class);
        Question question = question(9, 7);
        when(questionRepository.findById(9)).thenReturn(Optional.of(question));

        QuestionService legacyService = new QuestionService(questionRepository, quizRepository);

        assertThrows(IllegalStateException.class, () -> legacyService.getQuestionDetails(9));
    }

    private static List<Answer> answers(boolean persisted, boolean correct) {
        return java.util.stream.IntStream.range(0, 4).mapToObj(index -> {
            Answer answer = new Answer();
            answer.setId(persisted ? index + 1 : 0);
            answer.setAnswer("Answer " + index);
            answer.setCorrect(correct && index == 0);
            return answer;
        }).toList();
    }

    private static Question question(int id, int quizId) {
        Question question = new Question();
        question.setId(id);
        question.setQuizId(quizId);
        question.setQuestion("Question");
        return question;
    }

    private static final class Fixture {
        private final QuestionRepository questionRepository = mock(QuestionRepository.class);
        private final QuizRepository quizRepository = mock(QuizRepository.class);
        private final AnswerRepository answerRepository = mock(AnswerRepository.class);
        private final QuestionService service =
                new QuestionService(questionRepository, quizRepository, answerRepository);
    }
}

