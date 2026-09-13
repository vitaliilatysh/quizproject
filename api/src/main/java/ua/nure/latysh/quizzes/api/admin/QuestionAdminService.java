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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        List<Answer> targets = pairWithStoredRows(questionId, answers, request.answers());
        for (int index = 0; index < targets.size(); index++) {
            AnswerRequest answerRequest = request.answers().get(index);
            Answer answer = targets.get(index);
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
     * Which stored row each submitted option is an edit of.
     *
     * <p>By id when the request carries them, so the order of the list stops
     * meaning anything. Before, the two were paired by position: the same four
     * options sent in a different order rewrote each other's rows. Nothing was
     * corrupted by that — text and correctness travel together, so the question
     * still asked what the administrator meant — but an answer's id came to name
     * a different option, and ids are what {@code results} rows and an attempt's
     * snapshot are written against. An identifier that changes meaning is worth
     * ending before something starts reading those back.
     *
     * <p>Position still decides when no ids are sent, because a client that has
     * always echoed the order it was given is not wrong and had nothing else to
     * send until now. Mixing the two is refused rather than guessed at: a
     * request that names some rows and not others has not said what it wants.
     *
     * @throws InvalidRequestException if the ids are partial, repeated, or name
     *     rows that belong to another question
     */
    private List<Answer> pairWithStoredRows(
            int questionId, List<Answer> stored, List<AnswerRequest> submitted) {
        List<Integer> submittedIds = submitted.stream().map(AnswerRequest::id).filter(Objects::nonNull).toList();
        if (submittedIds.isEmpty()) {
            return stored;
        }
        if (submittedIds.size() != submitted.size()) {
            throw new InvalidRequestException(
                    "Either every answer names the row it edits, or none of them do");
        }
        Map<Integer, Answer> byId = stored.stream()
                .collect(Collectors.toMap(Answer::getId, answer -> answer));
        if (Set.copyOf(submittedIds).size() != submittedIds.size()) {
            throw new InvalidRequestException("An answer was named twice");
        }
        return submittedIds.stream()
                .map(id -> {
                    Answer answer = byId.get(id);
                    if (answer == null) {
                        throw new InvalidRequestException(
                                "Answer " + id + " does not belong to question " + questionId);
                    }
                    return answer;
                })
                .toList();
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
