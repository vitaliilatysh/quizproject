package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.QuizSpecifications;
import java.util.List;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for the public catalogue: listing, paging, search, filtering and the summary. */
class QuizCatalogueContractTest extends ApiContractTestBase {
    @Test
    void composesIndependentFiltersAndCountsMatchesAcrossPages() {
        QuizRepository repository = applicationContext.getBean(QuizRepository.class);
        var java = QuizSpecifications.nameOrSubjectContains("  JAVA  ");
        var medium = QuizSpecifications.hasAnyComplexity(List.of(" medium ", "MEDIUM"));

        var either = java.or(medium);
        var first = repository.findCatalogue(either, PageRequest.of(0, 1, Sort.by("id").descending()));
        var second = repository.findCatalogue(either, PageRequest.of(1, 1));
        assertThat(first.getContent()).extracting(Quiz::getId).containsExactly(1);
        assertThat(second.getContent()).extracting(Quiz::getId).containsExactly(2);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);

        assertThat(repository.findCatalogue(java.and(medium), Pageable.unpaged()).getContent()).isEmpty();
    }

    @Test
    void fetchesCatalogueRelationsBeforeTheRepositoryTransactionCloses() {
        QuizRepository repository = applicationContext.getBean(QuizRepository.class);
        var quizzes = repository.findCatalogue(QuizSpecifications.matching(null, null), Pageable.unpaged());
        var persistence = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(quizzes.getContent()).extracting(Quiz::getId).containsExactly(1, 2);
        for (Quiz quiz : quizzes) {
            assertThat(persistence.isLoaded(quiz, "subject")).isTrue();
            assertThat(persistence.isLoaded(quiz, "level")).isTrue();
        }
    }

    @Test
    @Transactional
    void findsLiteralWildcardsAndTheEscapeCharacterInNamesAndSubjects() {
        jdbcTemplate.update("UPDATE quizzes SET name = ? WHERE id = 1", "50%_! quiz");
        jdbcTemplate.update("UPDATE subjects SET name = ? WHERE id = 2", "50%_! subject");

        var page = quizQueryService.findAll("  50%_!  ", null, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting("id").containsExactly(1);
        assertThat(quizQueryService.findAll("50%_!", List.of("medium"), Pageable.unpaged()).getContent())
                .extracting("id").containsExactly(2);
    }

    @Test
    void listsQuizzesAndReturnsOneById() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "100"))
                .andExpect(header().string("X-Page-Number", "0"))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Java syntax"))
                .andExpect(jsonPath("$[0].subject").value("Java Basics"))
                .andExpect(jsonPath("$[0].complexity").value("low"))
                .andExpect(jsonPath("$[0].timeToPassMinutes").value(5))
                .andExpect(jsonPath("$[0].totalQuestions").value(2))
                .andExpect(jsonPath("$[1].totalQuestions").value(0));

        mockMvc.perform(get("/api/v1/quizzes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lists"));
    }

    @Test
    void paginatesCollectionEndpointsWithoutChangingTheirArrayShape() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Number", "1"))
                .andExpect(header().string("X-Page-Size", "1"))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(header().string("X-Total-Pages", "2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2));
        mockMvc.perform(get("/api/v1/quizzes").param("page", "99").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/v1/quizzes").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/quizzes").param("size", "101"))
                .andExpect(status().isBadRequest());

        String studentToken = login("student", "secret123", "192.0.2.13");
        mockMvc.perform(get("/api/v1/results/me")
                        .param("page", "0").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Size", "1"))
                .andExpect(jsonPath("$.length()").value(1));

        String adminToken = login("admin", "secret123", "192.0.2.14");
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Number", "0"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("page", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Size", "20"))
                .andExpect(header().exists("X-Total-Count"));
        mockMvc.perform(get("/api/v1/admin/quizzes")
                        .param("page", "99").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsConsistentErrorsForMissingAndInvalidQuizIdentifiers() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Quiz 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/quizzes/99"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/quizzes/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    // The level filter is a list, and a list can hold entries no level ever
    // matches. Over HTTP `?complexity=` arrives as one blank string; a caller
    // inside the process can pass a null just as easily, and both have to drop
    // out before the query is built — an empty IN clause fails outright.
    @Test
    void ignoresLevelLabelsThatAreBlankOrAbsent() {
        var everything = quizQueryService.findAll(null, null, PageRequest.of(0, 20));
        var withJunk = quizQueryService.findAll(
                null, Arrays.asList(null, "   ", ""), PageRequest.of(0, 20));
        var lowOnly = quizQueryService.findAll(
                null, Arrays.asList(null, "  ", " LOW "), PageRequest.of(0, 20));

        assertThat(withJunk.getTotalElements())
                .as("a list of blanks narrowed the catalogue instead of being ignored")
                .isEqualTo(everything.getTotalElements());
        assertThat(lowOnly.getContent()).extracting("complexity").containsOnly("low");
        assertThat(lowOnly.getTotalElements()).isLessThan(everything.getTotalElements());
    }

    @Test
    void searchesQuizzesByNameAndSubjectInTheDatabase() throws Exception {
        // "Java syntax" sits under subject "Java Basics", so it would match on
        // either column. "Lists" and "Collections" separate the two cleanly.
        mockMvc.perform(catalogue("192.0.2.80").param("search", "lists"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Lists"));

        mockMvc.perform(catalogue("192.0.2.80").param("search", "collections"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].subject").value("Collections"));

        mockMvc.perform(catalogue("192.0.2.80").param("search", "JAVA"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("Java syntax"));

        mockMvc.perform(catalogue("192.0.2.80").param("search", "nothing matches this"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(content().json("[]"));
    }

    @Test
    void treatsWildcardsInTheSearchTermAsLiteralText() throws Exception {
        // Without ESCAPE the pattern "%%%" matches every row, so a broken escape
        // shows up here as two results instead of none.
        mockMvc.perform(catalogue("192.0.2.81").param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));

        mockMvc.perform(catalogue("192.0.2.81").param("search", "_"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void filtersQuizzesByStoredLevelLabel() throws Exception {
        mockMvc.perform(catalogue("192.0.2.82").param("complexity", "low"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].complexity").value("low"));

        mockMvc.perform(catalogue("192.0.2.82").param("complexity", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].complexity").value("medium"));

        // Repeating the parameter is how a client groups several stored labels
        // under one control without the API hard-coding that grouping.
        mockMvc.perform(catalogue("192.0.2.82")
                        .param("complexity", "low")
                        .param("complexity", "medium"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));

        mockMvc.perform(catalogue("192.0.2.82").param("complexity", "advanced"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void treatsBlankFilterValuesAsAbsent() throws Exception {
        // A blank arrives as a non-empty list that normalises to nothing. Reading
        // emptiness off the raw list would send an empty IN clause and fail.
        mockMvc.perform(catalogue("192.0.2.83").param("complexity", ""))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));

        mockMvc.perform(catalogue("192.0.2.83").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"));
    }

    @Test
    void combinesSearchComplexityAndPagingInOneQuery() throws Exception {
        mockMvc.perform(catalogue("192.0.2.84")
                        .param("search", "java")
                        .param("complexity", "low"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("Java syntax"));

        // Contradictory filters must narrow, not fall back to everything.
        mockMvc.perform(catalogue("192.0.2.84")
                        .param("search", "java")
                        .param("complexity", "medium"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"));

        // The count header reports matches, not the page, so a filtered result
        // set still pages correctly.
        mockMvc.perform(catalogue("192.0.2.84")
                        .param("complexity", "low")
                        .param("complexity", "medium")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(header().string("X-Total-Pages", "2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    void rejectsOversizedFilterValues() throws Exception {
        mockMvc.perform(catalogue("192.0.2.85").param("search", "x".repeat(51)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(catalogue("192.0.2.85").param("complexity", "x".repeat(26)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsCatalogueTotalsWithoutFetchingTheCatalogue() throws Exception {
        // The fixture holds two quizzes across two subjects.
        mockMvc.perform(get("/api/v1/quizzes/summary").with(request -> {
                    request.setRemoteAddr("192.0.2.86");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andExpect(jsonPath("$.totalQuizzes").value(2))
                .andExpect(jsonPath("$.totalSubjects").value(2));
    }

    @Test
    void routesSummaryAheadOfTheQuizIdPathVariable() throws Exception {
        // /summary must not be bound as {quizId}; if it ever is, int binding
        // rejects it and this turns into a 400 rather than a summary.
        mockMvc.perform(get("/api/v1/quizzes/summary").with(request -> {
                    request.setRemoteAddr("192.0.2.87");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuizzes").isNumber());

        // A real id still resolves, and a missing one still 404s.
        mockMvc.perform(get("/api/v1/quizzes/1").with(request -> {
                    request.setRemoteAddr("192.0.2.87");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Java syntax"));
        mockMvc.perform(get("/api/v1/quizzes/9999").with(request -> {
                    request.setRemoteAddr("192.0.2.87");
                    return request;
                }))
                .andExpect(status().isNotFound());
    }
}
