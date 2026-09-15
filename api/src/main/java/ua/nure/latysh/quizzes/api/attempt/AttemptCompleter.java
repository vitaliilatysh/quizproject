package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestion;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.Result;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates and scores the one allowed completion of an owned attempt. */
@Component
final class AttemptCompleter {
    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final AttemptSnapshotStore snapshots;
    private final Clock clock;
    private final QuizMetrics metrics;

    @Autowired
    AttemptCompleter(
            AttemptRepository attemptRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            AttemptSnapshotStore snapshots,
            QuizMetrics metrics) {
        this(attemptRepository, answerRepository, resultRepository, snapshots, Clock.systemUTC(), metrics);
    }

    AttemptCompleter(
            AttemptRepository attemptRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            AttemptSnapshotStore snapshots,
            Clock clock,
            QuizMetrics metrics) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.snapshots = snapshots;
        this.clock = clock;
        this.metrics = metrics;
    }

    AttemptCompletionResponse complete(int attemptId, String username, Set<Integer> answerIds) {
        Attempt attempt = attemptRepository.findByIdAndUserLoginForUpdate(attemptId, username)
                .orElseThrow(() -> missingAttempt(attemptId));
        if (attempt.isCompleted()) {
            throw new ResourceConflictException("Attempt " + attemptId + " was already completed");
        }
        Instant completedAt = clock.instant();
        if (completedAt.isAfter(attempt.getExpiresAt())) {
            throw new ResourceConflictException("Attempt " + attemptId + " has expired");
        }

        AnswerKey answerKey = answerKeyOf(snapshots.find(attempt));
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

    // Both the permitted answers and the correct set come from the immutable
    // snapshot, never from whatever the administrator has since changed.
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
     * Records the selected options that still have a live answer row.
     *
     * <p>The score is already calculated from the snapshot. An answer deleted
     * mid-attempt cannot be inserted into {@code results} because of its foreign
     * key, so omitting that historical row preserves completion without changing
     * the score.
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

    private record AnswerKey(
            Set<Integer> knownAnswerIds,
            Map<Integer, Set<Integer>> correctByQuestion,
            Map<Integer, Integer> answerToQuestion) {
    }
}
