package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestion;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestionRepository;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.Question;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.Result;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads run in one read-only transaction so that every statement a request
 * issues sees the same snapshot. Without this each repository call opened its
 * own session on its own connection, so a multi-query read could observe a
 * database that changed underneath it.
 *
 * <p>The isolation level is pinned rather than inherited because sharing a
 * transaction is not by itself enough: under {@code READ COMMITTED} every
 * statement takes a fresh snapshot, so a concurrent commit is still visible
 * between two queries of the same read. MySQL defaults to repeatable read and
 * would behave correctly by accident; stating it here keeps the guarantee from
 * depending on how a given database happens to be configured.
 *
 * <p>The write methods below carry their own {@code @Transactional}, because
 * under a read-only transaction Hibernate never flushes and a modified entity
 * is discarded without an error. Each one repeats the isolation level, and a
 * new one must too: a method annotation replaces the class annotation rather
 * than adding to it, so a bare {@code @Transactional} silently drops the pin
 * above and leaves the write at whatever the database defaults to. That is the
 * accident this class was written to avoid. ApiContractTest asserts it for
 * every transactional method here, so the rule does not rest on this comment.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AttemptService {
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptQuestionRepository attemptQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final Clock clock;
    private final QuizMetrics metrics;

    @Autowired
    public AttemptService(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            AttemptQuestionRepository attemptQuestionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            QuizMetrics metrics) {
        this(quizRepository, userRepository, attemptRepository, attemptQuestionRepository, questionRepository,
                answerRepository, resultRepository, Clock.systemUTC(), metrics);
    }

    AttemptService(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            AttemptQuestionRepository attemptQuestionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            Clock clock,
            QuizMetrics metrics) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AttemptResponse start(int quizId, String username) {
        Quiz quiz = requireReadyQuiz(quizId);
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(quiz.getTimeToPass(), ChronoUnit.MINUTES);
        var attempt = new Attempt(startedAt, expiresAt, quiz, user);
        attemptRepository.saveAndFlush(attempt);
        // Written before the response is built, and the response is built from
        // it: what the reader is handed and what they will be scored against
        // are then the same list by construction, not by two reads agreeing.
        List<AttemptQuestion> snapshot = attemptQuestionRepository.saveAll(copyOfQuiz(attempt, quiz.getId()));
        AttemptResponse response = toResponse(attempt, snapshot);
        metrics.recordStartedAttempt();
        return response;
    }

    public AttemptResponse findOwned(int attemptId, String username) {
        Attempt attempt = attemptRepository.findByIdAndUserLogin(attemptId, username)
                .orElseThrow(() -> missingAttempt(attemptId));
        return toResponse(attempt, snapshotOf(attempt));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AttemptCompletionResponse complete(int attemptId, String username, Set<Integer> answerIds) {
        Attempt attempt = attemptRepository.findByIdAndUserLoginForUpdate(attemptId, username)
                .orElseThrow(() -> missingAttempt(attemptId));
        if (attempt.isCompleted()) {
            throw new ResourceConflictException("Attempt " + attemptId + " was already completed");
        }
        Instant completedAt = clock.instant();
        if (completedAt.isAfter(attempt.getExpiresAt())) {
            throw new ResourceConflictException("Attempt " + attemptId + " has expired");
        }

        AnswerKey answerKey = answerKeyOf(snapshotOf(attempt));
        if (answerKey.correctByQuestion().isEmpty()) {
            throw new ResourceConflictException("The attempted quiz no longer contains valid questions");
        }
        Set<Integer> selectedAnswers = Set.copyOf(answerIds);
        if (!answerKey.knownAnswerIds().containsAll(selectedAnswers)) {
            throw new InvalidRequestException("An answer does not belong to the attempted quiz");
        }

        int score = calculateScore(answerKey, selectedAnswers);
        saveAnswers(attempt, selectedAnswers);
        attempt.setScore(score);
        attempt.setEndTime(completedAt);
        attempt.setCompleted(true);
        metrics.recordCompletedAttempt(score);
        return new AttemptCompletionResponse(attemptId, attempt.getQuiz().getId(), score, completedAt);
    }

    private Quiz requireReadyQuiz(int quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz " + quizId + " was not found"));
        long invalidQuestions = questionRepository.countInvalidQuestions(quizId);
        long totalQuestions = questionRepository.countByQuiz_Id(quizId);
        if (totalQuestions == 0 || invalidQuestions > 0) {
            throw new ResourceConflictException("Quiz " + quizId + " is not ready for attempts");
        }
        return quiz;
    }

    /**
     * The snapshot this attempt was issued with.
     *
     * <p>The fallback covers exactly one window: a rolling deployment, where an
     * attempt started by a pod that had not yet migrated is completed by one
     * that has. Copying the quiz as it stands is what this service did for
     * every attempt before the snapshot existed, so an attempt from that window
     * is no worse off than it was — and every attempt started since has rows of
     * its own, including the ones the migration backfilled.
     */
    private List<AttemptQuestion> snapshotOf(Attempt attempt) {
        List<AttemptQuestion> stored =
                attemptQuestionRepository.findAllByAttempt_IdOrderByQuestionIdAscAnswerIdAsc(attempt.getId());
        return stored.isEmpty() ? copyOfQuiz(attempt, attempt.getQuiz().getId()) : stored;
    }

    /** The quiz as it stands, in the shape the snapshot stores it. */
    private List<AttemptQuestion> copyOfQuiz(Attempt attempt, int quizId) {
        var questionTexts = new LinkedHashMap<Integer, String>();
        for (Question question : questionRepository.findAllByQuiz_IdOrderByIdAsc(quizId)) {
            questionTexts.put(question.getId(), question.getQuestion());
        }
        return answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(quizId).stream()
                .map(answer -> new AttemptQuestion(
                        attempt,
                        answer.getQuestion().getId(),
                        questionTexts.get(answer.getQuestion().getId()),
                        answer.getId(),
                        answer.getAnswer(),
                        answer.isCorrect()))
                .toList();
    }

    private AttemptResponse toResponse(Attempt attempt, List<AttemptQuestion> snapshot) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getStartTime(),
                attempt.getExpiresAt(),
                attempt.isCompleted(),
                attempt.isCompleted() ? attempt.getScore() : null,
                attempt.getEndTime(),
                questionsOf(snapshot));
    }

    private static List<AttemptQuestionResponse> questionsOf(List<AttemptQuestion> snapshot) {
        var questions = new LinkedHashMap<Integer, MutableQuestion>();
        for (AttemptQuestion option : snapshot) {
            questions
                    .computeIfAbsent(option.getQuestionId(), ignored -> new MutableQuestion(option.getQuestionText()))
                    .answers()
                    .add(new AnswerOptionResponse(option.getAnswerId(), option.getAnswerText()));
        }
        return questions.entrySet().stream()
                .map(entry -> new AttemptQuestionResponse(
                        entry.getKey(), entry.getValue().text(), List.copyOf(entry.getValue().answers())))
                .toList();
    }

    // Which answers this attempt may carry, and which of them were the correct
    // ones — both as of the moment it started, whatever the quiz says now.
    private static AnswerKey answerKeyOf(List<AttemptQuestion> snapshot) {
        var knownAnswerIds = new HashSet<Integer>();
        var correctByQuestion = new LinkedHashMap<Integer, Set<Integer>>();
        var answerToQuestion = new HashMap<Integer, Integer>();
        for (AttemptQuestion option : snapshot) {
            int questionId = option.getQuestionId();
            int answerId = option.getAnswerId();
            knownAnswerIds.add(answerId);
            answerToQuestion.put(answerId, questionId);
            Set<Integer> correct = correctByQuestion.computeIfAbsent(questionId, ignored -> new HashSet<>());
            if (option.isCorrect()) {
                correct.add(answerId);
            }
        }
        return new AnswerKey(
                Set.copyOf(knownAnswerIds),
                Map.copyOf(correctByQuestion),
                Map.copyOf(answerToQuestion));
    }

    private static int calculateScore(AnswerKey answerKey, Set<Integer> selectedAnswers) {
        var selectedByQuestion = new HashMap<Integer, Set<Integer>>();
        answerKey.answerToQuestion().forEach((answerId, questionId) -> {
            if (selectedAnswers.contains(answerId)) {
                selectedByQuestion.computeIfAbsent(questionId, ignored -> new HashSet<>()).add(answerId);
            }
        });
        long correctQuestions = answerKey.correctByQuestion().entrySet().stream()
                .filter(entry -> entry.getValue().equals(
                        selectedByQuestion.getOrDefault(entry.getKey(), Set.of())))
                .count();
        return (int) (correctQuestions * 100 / answerKey.correctByQuestion().size());
    }

    /**
     * Records which options the reader ticked, for the ones still on the quiz.
     *
     * <p>`results.answer_id` is a foreign key to `answers`, so a row can only be
     * written for an answer that still exists. Since an attempt is now scored
     * against the snapshot it was issued with, a submission may legitimately
     * name an answer the administrator has deleted since — and inserting a
     * result for it fails the constraint and rolls the whole completion back,
     * which is the very thing the snapshot exists to prevent.
     *
     * <p>So the ones that are gone are left out rather than allowed to lose the
     * attempt. Nothing is lost by that beyond what was already lost: the
     * foreign key cascades, so a result for an answer deleted a moment later
     * would have been removed anyway. The score does not come from here — it
     * comes from the snapshot, which still holds every option and which of them
     * were correct.
     */
    private void saveAnswers(Attempt attempt, Set<Integer> selectedAnswers) {
        if (selectedAnswers.isEmpty()) {
            return;
        }
        List<Result> results = answerRepository.findAllById(selectedAnswers).stream()
                .map(answer -> new Result(answer, attempt))
                .toList();
        resultRepository.saveAll(results);
    }

    private static ResourceNotFoundException missingAttempt(int attemptId) {
        return new ResourceNotFoundException("Attempt " + attemptId + " was not found");
    }

    private record MutableQuestion(String text, List<AnswerOptionResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }

    private record AnswerKey(
            Set<Integer> knownAnswerIds,
            Map<Integer, Set<Integer>> correctByQuestion,
            Map<Integer, Integer> answerToQuestion) {
    }
}
