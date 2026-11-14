package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Composable catalogue filters, shared by the data query and its count query. */
public final class QuizSpecifications {
    private QuizSpecifications() {
    }

    public static Specification<Quiz> matching(String search, Collection<String> complexities) {
        return nameOrSubjectContains(search).and(hasAnyComplexity(complexities));
    }

    public static Specification<Quiz> nameOrSubjectContains(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }
        // Treat LIKE metacharacters as text, including the escape character itself.
        String pattern = "%" + search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("name")), pattern, '!'),
                builder.like(builder.lower(root.join("subject").get("name")), pattern, '!'));
    }

    public static Specification<Quiz> hasAnyComplexity(Collection<String> complexities) {
        List<String> labels = complexities == null ? List.of() : complexities.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        // No predicate is built for an empty set, so MySQL never receives IN ().
        if (labels.isEmpty()) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.lower(root.join("level").get("label")).in(labels);
    }
}
