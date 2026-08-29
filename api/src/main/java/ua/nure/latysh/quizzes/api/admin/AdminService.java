package ua.nure.latysh.quizzes.api.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.LevelResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.ResultResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.SubjectResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.domain.Answer;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.Level;
import ua.nure.latysh.quizzes.api.domain.LevelRepository;
import ua.nure.latysh.quizzes.api.domain.Question;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.Subject;
import ua.nure.latysh.quizzes.api.domain.SubjectRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads run in one read-only transaction so that every statement a request
 * issues sees the same snapshot. Without this each repository call opened its
 * own session on its own connection, so a multi-query read could observe a
 * database that changed underneath it.
 *
 * <p>The write methods below override this with their own
 * {@code @Transactional}. A new method that writes must do the same: under a
 * read-only transaction Hibernate never flushes, so a modified entity is
 * discarded without an error.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AttemptRepository attemptRepository;
    private final ResultRepository resultRepository;
    private final UserRepository userRepository;
    private final StatusRepository statusRepository;

    public AdminService(
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            AttemptRepository attemptRepository,
            ResultRepository resultRepository,
            UserRepository userRepository,
            StatusRepository statusRepository) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.statusRepository = statusRepository;
    }

    public List<SubjectResponse> subjects() {
        return subjectRepository.findAllByOrderByNameAsc().stream()
                .map(subject -> new SubjectResponse(subject.getId(), subject.getName()))
                .toList();
    }

    public List<LevelResponse> levels() {
        return levelRepository.findAllByOrderByIdAsc().stream()
                .map(level -> new LevelResponse(level.getId(), level.getLabel()))
                .toList();
    }

    public Page<QuizResponse> quizzes(Pageable pageable) {
        // The admin listing has no search or level filter of its own, so it asks
        // for every quiz through the same query the public catalogue uses.
        Page<Quiz> quizzes = quizRepository.search(
                null, true, QuizRepository.ANY_COMPLEXITY, pageable);
        Map<Integer, Long> questionCounts = questionCountsByQuizId(quizzes.getContent());
        return quizzes.map(quiz -> toQuizResponse(quiz, questionCounts.getOrDefault(quiz.getId(), 0L)));
    }

    public List<QuestionResponse> questions(int quizId) {
        requireExistsQuiz(quizId);
        return questionsWithAnswers(quizId);
    }

    @Transactional
    public SubjectResponse createSubject(String name) {
        String normalizedName = name.trim();
        var subject = new Subject(normalizedName);
        try {
            subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Subject", normalizedName);
        }
        return new SubjectResponse(subject.getId(), normalizedName);
    }

    @Transactional
    public SubjectResponse updateSubject(int subjectId, String name) {
        String normalizedName = name.trim();
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> missing("Subject", subjectId));
        subject.setName(normalizedName);
        try {
            subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Subject", normalizedName);
        }
        return new SubjectResponse(subjectId, normalizedName);
    }

    @Transactional
    public void deleteSubject(int subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> missing("Subject", subjectId));
        if (quizRepository.existsBySubject_Id(subjectId)) {
            throw new ResourceConflictException("Subject " + subjectId + " is used by a quiz");
        }
        subjectRepository.delete(subject);
    }

    @Transactional
    public QuizResponse createQuiz(QuizRequest request) {
        Subject subject = requireSubject(request.subjectId());
        Level level = requireLevel(request.levelId());
        String name = request.name().trim();
        var quiz = new Quiz();
        quiz.setName(name);
        quiz.setTimeToPass(request.timeToPassMinutes());
        quiz.setLevel(level);
        quiz.setSubject(subject);
        try {
            quizRepository.saveAndFlush(quiz);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Quiz", name);
        }
        return toQuizResponse(quiz, 0);
    }

    @Transactional
    public QuizResponse updateQuiz(int quizId, QuizRequest request) {
        Quiz quiz = quizRepository.findByIdFetchingSubjectAndLevel(quizId)
                .orElseThrow(() -> missing("Quiz", quizId));
        Subject subject = requireSubject(request.subjectId());
        Level level = requireLevel(request.levelId());
        quiz.setName(request.name().trim());
        quiz.setTimeToPass(request.timeToPassMinutes());
        quiz.setLevel(level);
        quiz.setSubject(subject);
        try {
            quizRepository.saveAndFlush(quiz);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Quiz", request.name().trim());
        }
        return toQuizResponse(quiz, questionRepository.countByQuiz_Id(quizId));
    }

    @Transactional
    public void deleteQuiz(int quizId) {
        requireExistsQuiz(quizId);
        resultRepository.deleteAllByQuizId(quizId);
        answerRepository.deleteAllByQuizId(quizId);
        attemptRepository.deleteAllByQuizId(quizId);
        questionRepository.deleteAllByQuizId(quizId);
        quizRepository.deleteById(quizId);
    }

    @Transactional
    public QuestionResponse createQuestion(int quizId, QuestionRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> missing("Quiz", quizId));
        validateAnswers(request.answers());
        var question = new Question(request.text().trim(), quiz);
        questionRepository.saveAndFlush(question);
        insertAnswers(question, request.answers());
        return questionsWithAnswers(quizId).stream()
                .filter(response -> response.id() == question.getId())
                .findFirst()
                .orElseThrow(() -> missing("Question", question.getId()));
    }

    @Transactional
    public QuestionResponse updateQuestion(int questionId, QuestionRequest request) {
        validateAnswers(request.answers());
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> missing("Question", questionId));
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
        return questionsWithAnswers(question.getQuiz().getId()).stream()
                .filter(response -> response.id() == questionId)
                .findFirst()
                .orElseThrow(() -> missing("Question", questionId));
    }

    @Transactional
    public void deleteQuestion(int questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw missing("Question", questionId);
        }
        resultRepository.deleteAllByQuestionId(questionId);
        answerRepository.deleteAllByQuestionId(questionId);
        questionRepository.deleteById(questionId);
    }

    public Page<UserResponse> users(Pageable pageable) {
        return userRepository.findAllByOrderByLoginAsc(pageable)
                .map(user -> new UserResponse(
                        user.getId(), user.getLogin(), user.getRole().getName(), user.getStatus().getName()));
    }

    @Transactional
    public UserResponse updateUserStatus(int userId, String status, String currentUsername) {
        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> missing("User", userId));
        if (user.getLogin().equals(currentUsername) && "blocked".equals(normalizedStatus)) {
            throw new ResourceConflictException("An administrator cannot block the current account");
        }
        user.setStatus(requireStatus(normalizedStatus));
        return new UserResponse(user.getId(), user.getLogin(), user.getRole().getName(), user.getStatus().getName());
    }

    public Page<ResultResponse> results(Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Result range start must not be after its end");
        }
        return attemptRepository.findCompletedInRange(from, to, pageable)
                .map(attempt -> new ResultResponse(
                        attempt.getId(),
                        attempt.getUser().getLogin(),
                        attempt.getQuiz().getId(),
                        attempt.getQuiz().getName(),
                        attempt.getScore(),
                        attempt.getEndTime()));
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

    private Map<Integer, Long> questionCountsByQuizId(List<Quiz> quizzes) {
        if (quizzes.isEmpty()) {
            return Map.of();
        }
        List<Integer> quizIds = quizzes.stream().map(Quiz::getId).toList();
        return questionRepository.countAllGroupedByQuizIds(quizIds).stream()
                .collect(Collectors.toMap(
                        QuestionRepository.QuizQuestionCount::getQuizId,
                        QuestionRepository.QuizQuestionCount::getTotal));
    }

    private static QuizResponse toQuizResponse(Quiz quiz, long totalQuestions) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getName(),
                quiz.getTimeToPass(),
                quiz.getLevel().getId(),
                quiz.getLevel().getLabel(),
                quiz.getSubject().getId(),
                quiz.getSubject().getName(),
                (int) totalQuestions);
    }

    private Subject requireSubject(int subjectId) {
        return subjectRepository.findById(subjectId).orElseThrow(() -> missing("Subject", subjectId));
    }

    private Level requireLevel(int levelId) {
        return levelRepository.findById(levelId).orElseThrow(() -> missing("Level", levelId));
    }

    private Status requireStatus(String normalizedStatus) {
        return statusRepository.findByNameIgnoreCase(normalizedStatus)
                .orElseThrow(() -> new IllegalStateException("Status '" + normalizedStatus + "' is not configured"));
    }

    private void requireExistsQuiz(int quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw missing("Quiz", quizId);
        }
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

    private static ResourceNotFoundException missing(String resource, int id) {
        return new ResourceNotFoundException(resource + " " + id + " was not found");
    }

    private static ResourceConflictException duplicate(String resource, String name) {
        return new ResourceConflictException(resource + " named '" + name + "' already exists");
    }

    private record MutableQuestion(String text, List<AnswerResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }
}
