package ua.nure.latysh.quizzes.api.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QuizQueryService {
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    public QuizQueryService(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    public Page<QuizResponse> findAll(Pageable pageable) {
        Page<Quiz> quizzes = quizRepository.findAllFetchingSubjectAndLevel(pageable);
        Map<Integer, Long> questionCounts = questionCountsByQuizId(quizzes.getContent());
        return quizzes.map(quiz -> toResponse(quiz, questionCounts.getOrDefault(quiz.getId(), 0L)));
    }

    public QuizResponse findById(int quizId) {
        Quiz quiz = quizRepository.findByIdFetchingSubjectAndLevel(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz " + quizId + " was not found"));
        return toResponse(quiz, questionRepository.countByQuiz_Id(quizId));
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

    private static QuizResponse toResponse(Quiz quiz, long totalQuestions) {
        return new QuizResponse(
                quiz.getId(),
                quiz.getName(),
                quiz.getSubject().getName(),
                quiz.getLevel().getLabel(),
                quiz.getTimeToPass(),
                (int) totalQuestions);
    }
}
