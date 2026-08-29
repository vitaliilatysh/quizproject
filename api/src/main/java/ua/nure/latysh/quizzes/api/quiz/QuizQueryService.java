package ua.nure.latysh.quizzes.api.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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

    /**
     * Lists quizzes narrowed by an optional name or subject substring and an
     * optional set of level labels.
     *
     * <p>Filtering happens here rather than in the browser because the endpoint
     * is paginated: narrowing a single page client-side would hide every match
     * on the other pages.
     *
     * <p>Level labels are matched as stored ({@code low}, {@code medium},
     * {@code high}, {@code advanced}) rather than as the three buckets the web
     * client offers. A caller that groups several labels under one control sends
     * them all, which keeps the closed set in the database from being narrowed
     * to whatever one UI happens to display.
     */
    public Page<QuizResponse> findAll(String search, Collection<String> complexities, Pageable pageable) {
        // Decide "no level filter" from the normalised set, not the raw one. A
        // request like `?complexity=` arrives as a one-element list of blanks,
        // which is non-empty but normalises to nothing — reading emptiness off
        // the raw list would send an empty IN clause and fail the query.
        Collection<String> wanted = normalized(complexities);
        boolean allComplexities = wanted.isEmpty();
        Page<Quiz> quizzes = quizRepository.search(
                searchPattern(search),
                allComplexities,
                allComplexities ? QuizRepository.ANY_COMPLEXITY : wanted,
                pageable);
        Map<Integer, Long> questionCounts = questionCountsByQuizId(quizzes.getContent());
        return quizzes.map(quiz -> toResponse(quiz, questionCounts.getOrDefault(quiz.getId(), 0L)));
    }

    private static String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        // Escape the wildcards LIKE would otherwise honour, so a search for "50%"
        // looks for that text instead of matching everything.
        String escaped = search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static Collection<String> normalized(Collection<String> complexities) {
        if (complexities == null) {
            return List.of();
        }
        return complexities.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
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
