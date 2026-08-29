package ua.nure.latysh.quizzes.api.attempt;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.domain.Answer;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Attempt;
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

@Service
public class AttemptService {
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ResultRepository resultRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final QuizMetrics metrics;

    @Autowired
    public AttemptService(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            EntityManager entityManager,
            QuizMetrics metrics) {
        this(quizRepository, userRepository, attemptRepository, questionRepository, answerRepository,
                resultRepository, entityManager, Clock.systemUTC(), metrics);
    }

    AttemptService(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            ResultRepository resultRepository,
            EntityManager entityManager,
            Clock clock,
            QuizMetrics metrics) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.resultRepository = resultRepository;
        this.entityManager = entityManager;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public AttemptResponse start(int quizId, String username) {
        Quiz quiz = requireReadyQuiz(quizId);
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(quiz.getTimeToPass(), ChronoUnit.MINUTES);
        var attempt = new Attempt(startedAt, expiresAt, quiz, user);
        attemptRepository.saveAndFlush(attempt);
        AttemptResponse response = toResponse(attempt);
        metrics.recordStartedAttempt();
        return response;
    }

    public AttemptResponse findOwned(int attemptId, String username) {
        Attempt attempt = attemptRepository.findByIdAndUserLogin(attemptId, username)
                .orElseThrow(() -> missingAttempt(attemptId));
        return toResponse(attempt);
    }

    @Transactional
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

        AnswerKey answerKey = loadAnswerKey(attempt.getQuiz().getId());
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

    private AttemptResponse toResponse(Attempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getStartTime(),
                attempt.getExpiresAt(),
                attempt.isCompleted(),
                attempt.isCompleted() ? attempt.getScore() : null,
                attempt.getEndTime(),
                loadQuestions(attempt.getQuiz().getId()));
    }

    private List<AttemptQuestionResponse> loadQuestions(int quizId) {
        var questions = new LinkedHashMap<Integer, MutableQuestion>();
        for (Question question : questionRepository.findAllByQuiz_IdOrderByIdAsc(quizId)) {
            questions.put(question.getId(), new MutableQuestion(question.getQuestion()));
        }
        for (Answer answer : answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(quizId)) {
            var question = questions.get(answer.getQuestion().getId());
            if (question != null) {
                question.answers().add(new AnswerOptionResponse(answer.getId(), answer.getAnswer()));
            }
        }
        return questions.entrySet().stream()
                .map(entry -> new AttemptQuestionResponse(
                        entry.getKey(), entry.getValue().text(), List.copyOf(entry.getValue().answers())))
                .toList();
    }

    private AnswerKey loadAnswerKey(int quizId) {
        var knownAnswerIds = new HashSet<Integer>();
        var correctByQuestion = new LinkedHashMap<Integer, Set<Integer>>();
        var answerToQuestion = new HashMap<Integer, Integer>();
        for (Question question : questionRepository.findAllByQuiz_IdOrderByIdAsc(quizId)) {
            correctByQuestion.put(question.getId(), new HashSet<>());
        }
        for (Answer answer : answerRepository.findAllByQuestionQuizIdOrderByQuestionIdAndId(quizId)) {
            int questionId = answer.getQuestion().getId();
            int answerId = answer.getId();
            knownAnswerIds.add(answerId);
            answerToQuestion.put(answerId, questionId);
            if (answer.isCorrect()) {
                correctByQuestion.get(questionId).add(answerId);
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

    private void saveAnswers(Attempt attempt, Set<Integer> selectedAnswers) {
        if (selectedAnswers.isEmpty()) {
            return;
        }
        List<Result> results = selectedAnswers.stream()
                .map(answerId -> new Result(entityManager.getReference(Answer.class, answerId), attempt))
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
