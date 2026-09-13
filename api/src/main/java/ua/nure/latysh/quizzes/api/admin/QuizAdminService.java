package ua.nure.latysh.quizzes.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizResponse;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.Level;
import ua.nure.latysh.quizzes.api.domain.LevelRepository;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.domain.Subject;
import ua.nure.latysh.quizzes.api.domain.SubjectRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quizzes themselves: listing them, and creating, editing or removing one.
 *
 * <p>Their questions are {@link QuestionAdminService}'s, which is the line
 * between the two: this service decides that a quiz exists and what it is
 * called, that one decides what it asks. See the package documentation for
 * transactions.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class QuizAdminService {
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AttemptRepository attemptRepository;
    private final ResultRepository resultRepository;
    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;

    public QuizAdminService(
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            AttemptRepository attemptRepository,
            ResultRepository resultRepository,
            SubjectRepository subjectRepository,
            LevelRepository levelRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
    }

    public Page<QuizResponse> quizzes(Pageable pageable) {
        // The admin listing has no search or level filter of its own, so it asks
        // for every quiz through the same query the public catalogue uses.
        Page<Quiz> quizzes = quizRepository.search(
                null, true, QuizRepository.ANY_COMPLEXITY, pageable);
        Map<Integer, Long> questionCounts = questionCountsByQuizId(quizzes.getContent());
        return quizzes.map(quiz -> toQuizResponse(quiz, questionCounts.getOrDefault(quiz.getId(), 0L)));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QuizResponse createQuiz(QuizRequest request) {
        Subject subject = requireSubject(request.subjectId());
        Level level = requireLevel(request.levelId());
        String name = request.name().trim();
        var quiz = new Quiz();
        quiz.setName(name);
        quiz.setTimeToPass(request.timeToPassMinutes());
        quiz.setLevel(level);
        quiz.setSubject(subject);
        AdminErrors.saveUnique("Quiz", name, () -> quizRepository.saveAndFlush(quiz));
        return toQuizResponse(quiz, 0);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QuizResponse updateQuiz(int quizId, QuizRequest request) {
        Quiz quiz = quizRepository.findByIdFetchingSubjectAndLevel(quizId)
                .orElseThrow(() -> AdminErrors.missing("Quiz", quizId));
        Subject subject = requireSubject(request.subjectId());
        Level level = requireLevel(request.levelId());
        String name = request.name().trim();
        quiz.setName(name);
        quiz.setTimeToPass(request.timeToPassMinutes());
        quiz.setLevel(level);
        quiz.setSubject(subject);
        AdminErrors.saveUnique("Quiz", name, () -> quizRepository.saveAndFlush(quiz));
        return toQuizResponse(quiz, questionRepository.countByQuiz_Id(quizId));
    }

    /**
     * Removes the quiz and everything that only existed because of it.
     *
     * <p>The order is the order the foreign keys allow: results name answers and
     * attempts, answers name questions, attempts and questions name the quiz.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteQuiz(int quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw AdminErrors.missing("Quiz", quizId);
        }
        resultRepository.deleteAllByQuizId(quizId);
        answerRepository.deleteAllByQuizId(quizId);
        attemptRepository.deleteAllByQuizId(quizId);
        questionRepository.deleteAllByQuizId(quizId);
        quizRepository.deleteById(quizId);
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
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> AdminErrors.missing("Subject", subjectId));
    }

    private Level requireLevel(int levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> AdminErrors.missing("Level", levelId));
    }
}
