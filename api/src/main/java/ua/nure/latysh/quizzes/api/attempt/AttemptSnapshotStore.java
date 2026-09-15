package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.stereotype.Component;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestion;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Question;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Stores and projects the immutable quiz definition issued with an attempt. */
@Component
final class AttemptSnapshotStore {
    private final AttemptQuestionRepository attemptQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    AttemptSnapshotStore(
            AttemptQuestionRepository attemptQuestionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository) {
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    List<AttemptQuestion> create(Attempt attempt) {
        return attemptQuestionRepository.saveAll(copyOfQuiz(attempt, attempt.getQuiz().getId()));
    }

    /**
     * Returns the snapshot this attempt was issued with.
     *
     * <p>The live-quiz fallback covers rolling deployments from before snapshot
     * rows existed. Every new attempt receives stored rows through
     * {@link #create(Attempt)}.
     */
    List<AttemptQuestion> find(Attempt attempt) {
        List<AttemptQuestion> stored =
                attemptQuestionRepository.findAllByAttempt_IdOrderByQuestionIdAscAnswerIdAsc(attempt.getId());
        return stored.isEmpty() ? copyOfQuiz(attempt, attempt.getQuiz().getId()) : stored;
    }

    AttemptResponse toResponse(Attempt attempt, List<AttemptQuestion> snapshot) {
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

    private record MutableQuestion(String text, List<AnswerOptionResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }
}
