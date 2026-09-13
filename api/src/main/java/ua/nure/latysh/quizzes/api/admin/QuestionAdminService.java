package ua.nure.latysh.quizzes.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionResponse;
import ua.nure.latysh.quizzes.api.domain.Answer;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Question;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * What a quiz asks: its questions and the answers offered for each.
 *
 * <p>See the package documentation for transactions.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class QuestionAdminService {
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final QuizRepository quizRepository;

    public QuestionAdminService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            QuizRepository quizRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.quizRepository = quizRepository;
    }

    public List<QuestionResponse> questions(int quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw AdminErrors.missing("Quiz", quizId);
        }
        return questionsWithAnswers(quizId);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QuestionResponse createQuestion(int quizId, QuestionRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> AdminErrors.missing("Quiz", quizId));
        validateAnswers(request.answers());
        var question = new Question(request.text().trim(), quiz);
        questionRepository.saveAndFlush(question);
        insertAnswers(question, request.answers());
        return reread(quizId, question.getId());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QuestionResponse updateQuestion(int questionId, QuestionRequest request) {
        validateAnswers(request.answers());
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AdminErrors.missing("Question", questionId));
        question.setQuestion(request.text().trim());
        List<Answer> answers = answerRepository.findAllByQuestion_IdOrderByIdAsc(questionId);
        if (answers.size() != request.answers().size()) {
            throw new ResourceConflictException(
                    "Question " + questionId + " does not contain exactly four answers");
        }
        for (int index = 0; index < answers.size(); index++) {
            AnswerRequest answerRequest = request.answers().get(index);
            Answer answer = answers.get(index);
            answer.setAnswer(answerRequest.text().trim());
            answer.setCorrect(answerRequest.correct());
        }
        return reread(question.getQuiz().getId(), questionId);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteQuestion(int questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw AdminErrors.missing("Question", questionId);
        }
        resultRepository.deleteAllByQuestionId(questionId);
        answerRepository.deleteAllByQuestionId(questionId);
        questionRepository.deleteById(questionId);
    }

    /**
     * The question as it now stands, read back through the same path a listing
     * takes, so that what a write returns and what a later read returns cannot
     * disagree about the shape of it.
     */
    private QuestionResponse reread(int quizId, int questionId) {
        return questionsWithAnswers(quizId).stream()
                .filter(response -> response.id() == questionId)
                .findFirst()
                .orElseThrow(() -> AdminErrors.missing("Question", questionId));
    }

    private List<QuestionResponse> questionsWithAnswers(int quizId) {
        var questions = new LinkedHashMap<Integer, MutableQuestion>();
        for (Question question : questionRepository.findAllByQuiz_IdOrderByIdAsc(quizId)) {
            questions.put(question.getId(), new MutableQuestion(question.getQuestion()));
        }
        for (Answer answer : answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(quizId)) {
            var question = questions.get(answer.getQuestion().getId());
            if (question != null) {
                question.answers().add(new AnswerResponse(answer.getId(), answer.getAnswer(), answer.isCorrect()));
            }
        }
        return questions.entrySet().stream()
                .map(entry -> new QuestionResponse(
                        entry.getKey(), quizId, entry.getValue().text(), List.copyOf(entry.getValue().answers())))
                .toList();
    }

    private static void validateAnswers(List<AnswerRequest> answers) {
        if (answers.stream().noneMatch(AnswerRequest::correct)) {
            throw new InvalidRequestException("A question must have at least one correct answer");
        }
    }

    private void insertAnswers(Question question, List<AnswerRequest> answers) {
        List<Answer> rows = answers.stream()
                .map(answer -> new Answer(answer.text().trim(), answer.correct(), question))
                .toList();
        answerRepository.saveAll(rows);
    }

    private record MutableQuestion(String text, List<AnswerResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }
}
