package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDetailsService userDetailsService;

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

    @Test
    void exchangesCredentialsForBearerTokenAndReadsCurrentUserResults() throws Exception {
        String studentToken = login("student", "secret123", "192.0.2.10");

        mockMvc.perform(get("/api/v1/results/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("student", "secret123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(1))
                .andExpect(jsonPath("$[0].quizId").value(1))
                .andExpect(jsonPath("$[0].quizName").value("Java syntax"))
                .andExpect(jsonPath("$[0].score").value(80))
                .andExpect(jsonPath("$[0].completedAt").value("2026-08-12T10:15:30Z"));

        String emptyToken = login("empty", "secret123", "192.0.2.11");
        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(emptyToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void exchangesAStillValidTokenForAFreshOneOnRefresh() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        String originalToken = login("student", "secret123", "192.0.2.12");
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, bearer(originalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String refreshedToken = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("accessToken").asString();
        Assertions.assertNotEquals(originalToken, refreshedToken);

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(refreshedToken)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidBlockedAndMalformedLoginRequests() throws Exception {
        performLogin("missing", "secret123", "192.0.2.20")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        performLogin("blocked", "secret123", "192.0.2.21")
                .andExpect(status().isUnauthorized());

        performLogin("student", "wrong-password", "192.0.2.22")
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.23");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void registersProfilesAndChangesTheCurrentUsersPassword() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"short","firstName":"","lastName":"User","password":"spaces are bad"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.70");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student","firstName":"Existing","lastName":"User","password":"secret123"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.71");
                            return request;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already registered"));

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"p10user","firstName":"Vitalii","lastName":"Latysh","password":"initial123"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.72");
                            return request;
                        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("accessToken").asString();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("p10user"))
                .andExpect(jsonPath("$.firstName").value("Vitalii"))
                .andExpect(jsonPath("$.lastName").value("Latysh"))
                .andExpect(jsonPath("$.role").value("student"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.registeredAt").exists())
                .andExpect(jsonPath("$.lastLoginAt").exists());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"updated123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"initial123\",\"newPassword\":\"initial123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("New password must differ from the current password"));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"initial123\",\"newPassword\":\"updated123\"}"))
                .andExpect(status().isNoContent());

        performLogin("p10user", "initial123", "192.0.2.73")
                .andExpect(status().isUnauthorized());
        performLogin("p10user", "updated123", "192.0.2.74")
                .andExpect(status().isOk());
    }

    @Test
    void enforcesUserAndAdministratorRoles() throws Exception {
        String userToken = login("student", "secret123", "192.0.2.30");
        mockMvc.perform(get("/api/v1/admin/status").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"));

        String adminToken = login("admin", "secret123", "192.0.2.31");
        mockMvc.perform(get("/api/v1/admin/status").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("quiz-api"))
                .andExpect(jsonPath("$.access").value("admin"));
    }

    @Test
    void providesCompleteAdministrativeContentManagement() throws Exception {
        String userToken = login("student", "secret123", "192.0.2.60");
        String adminToken = login("admin", "secret123", "192.0.2.61");

        mockMvc.perform(get("/api/v1/admin/subjects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Collections"));
        mockMvc.perform(get("/api/v1/admin/levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("low"));
        mockMvc.perform(get("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectId").value(1));

        int subjectId = responseId(mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Data Science \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Data Science"))
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java Basics\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Data Engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Data Engineering"));
        mockMvc.perform(put("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java Basics\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admin/subjects/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/subjects/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Subject 1 is used by a quiz"));

        String quizRequest = """
                {"name":"Data structures","subjectId":%d,"levelId":1,"timeToPassMinutes":15}
                """.formatted(subjectId);
        int quizId = responseId(mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quizRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Data Engineering"))
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java syntax\",\"subjectId\":1,\"levelId\":1,\"timeToPassMinutes\":5}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing subject\",\"subjectId\":999,\"levelId\":1,\"timeToPassMinutes\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing level\",\"subjectId\":1,\"levelId\":999,\"timeToPassMinutes\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad duration\",\"subjectId\":1,\"levelId\":1,\"timeToPassMinutes\":0}"))
                .andExpect(status().isBadRequest());

        String updatedQuiz = """
                {"name":"Algorithms","subjectId":%d,"levelId":2,"timeToPassMinutes":20}
                """.formatted(subjectId);
        mockMvc.perform(put("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuiz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Algorithms"))
                .andExpect(jsonPath("$.complexity").value("medium"));
        mockMvc.perform(put("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lists\",\"subjectId\":2,\"levelId\":2,\"timeToPassMinutes\":10}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admin/quizzes/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuiz))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/v1/admin/quizzes/999/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        String questionRequest = """
                {"text":"What is a stack?","answers":[
                  {"text":"LIFO","correct":true},{"text":"FIFO","correct":false},
                  {"text":"Tree","correct":false},{"text":"Graph","correct":false}]}
                """;
        int questionId = responseId(mockMvc.perform(post(
                                "/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(4))
                .andReturn());

        String updatedQuestion = questionRequest.replace("What is a stack?", "Choose LIFO")
                .replace("\"LIFO\"", "\"Stack\"");
        mockMvc.perform(put("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Choose LIFO"))
                .andExpect(jsonPath("$.answers[0].text").value("Stack"));
        mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest.replace("true", "false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A question must have at least one correct answer"));
        mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Too few\",\"answers\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/quizzes/999/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("INSERT INTO questions (id, question, quiz_id) VALUES (99, 'Broken', ?)", quizId);
        mockMvc.perform(put("/api/v1/admin/questions/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Question 99 does not contain exactly four answers"));
        mockMvc.perform(delete("/api/v1/admin/questions/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/admin/questions/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked"));
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(patch("/api/v1/admin/users/5/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/users/999/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username")
                        .value(org.hamcrest.Matchers.hasItem("student")));
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("from", "2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("to", "2026-12-31T23:59:59Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("from", "2027-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void completesAnOwnedQuizAttemptWithoutLeakingCorrectAnswers() throws Exception {
        String token = login("apiuser", "secret123", "192.0.2.50");
        MvcResult started = startAttempt(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quizId").value(1))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].text").value("Question 1"))
                .andExpect(jsonPath("$.questions[0].answers.length()").value(4))
                .andExpect(jsonPath("$.questions[0].answers[0].text").value("Answer 1.1"))
                .andExpect(jsonPath("$.questions[0].answers[0].correct").doesNotExist())
                .andReturn();
        long attemptId = objectMapper.readTree(started.getResponse().getContentAsString()).get("attemptId").asLong();

        mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId));

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[1,5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.quizId").value(1))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Attempt " + attemptId + " was already completed"));
    }

    @Test
    void rejectsInvalidUnauthorizedAndStaleAttemptOperations() throws Exception {
        mockMvc.perform(post("/api/v1/quizzes/1/attempts"))
                .andExpect(status().isUnauthorized());

        String token = login("apiuser", "secret123", "192.0.2.51");
        mockMvc.perform(post("/api/v1/quizzes/0/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/quizzes/99/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Quiz 99 was not found"));
        mockMvc.perform(post("/api/v1/quizzes/2/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quiz 2 is not ready for attempts"));
        mockMvc.perform(get("/api/v1/attempts/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attempts/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/attempts/2/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Attempt 2 has expired"));
        mockMvc.perform(post("/api/v1/attempts/3/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The attempted quiz no longer contains valid questions"));

        long invalidAnswerAttempt = attemptId(startAttempt(token).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An answer does not belong to the attempted quiz"));

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[0]}"))
                .andExpect(status().isBadRequest());

        long unansweredAttempt = attemptId(startAttempt(token).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", unansweredAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void rejectsATokenWhoseUserWasRemovedAfterLogin() throws Exception {
        String token = login("orphan", "secret123", "192.0.2.52");
        jdbcTemplate.update("DELETE FROM users WHERE id = 7");
        try {
            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Current user was not found"));
            mockMvc.perform(put("/api/v1/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"updated123\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Current user was not found"));
        } finally {
            jdbcTemplate.update("""
                    INSERT INTO users VALUES (
                        7, 'orphan', 'secret123', 'Orphan', 'User',
                        TIMESTAMP '2025-01-07 09:00:00', NULL, 1, 2
                    )
                    """);
        }
    }

    @Test
    void appliesCorsAllowlistWithoutRateLimitingPreflightRequests() throws Exception {
        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Correlation-ID")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Total-Count")))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));

        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rateLimitsRepeatedLoginAttemptsByRemoteAddress() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            performLogin("student", "wrong-password", "192.0.2.40")
                    .andExpect(status().isUnauthorized());
        }
        performLogin("student", "secret123", "192.0.2.40")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }

    @Test
    void healthAndApiDocumentationArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-ID", "contract-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "contract-request-123"))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        String adminToken = login("admin", "secret123", "192.0.2.90");
        mockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
        mockMvc.perform(get("/actuator/metrics/quiz.rate.limit.requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("quiz.rate.limit.requests"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("quiz_authentication_attempts_total")))
                .andExpect(content().string(containsString("quiz_rate_limit_requests_total")))
                .andExpect(content().string(containsString("http_server_requests_seconds")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Quiz REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password, String remoteAddress) throws Exception {
        MvcResult result = performLogin(username, password, remoteAddress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private org.springframework.test.web.servlet.ResultActions startAttempt(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long attemptId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("attemptId").asLong();
    }

    private int responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            String username, String password, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // Each of these takes its own client address: the catalogue shares one
    // rate-limit bucket per IP, and draining the default one fails whichever
    // test happens to run afterwards.
    private MockHttpServletRequestBuilder catalogue(String remoteAddress) {
        return get("/api/v1/quizzes").with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        });
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

    @Test
    void rehashesLegacyPlainTextPasswordsOnSuccessfulLogin() throws Exception {
        // The legacy schema stored passwords in plain text (VARCHAR(15) could
        // hold nothing else) and V2 only widened the column, so migrated rows
        // stay readable until something re-encodes them. This fixture is in
        // exactly that state, which is why every other login test works.
        jdbcTemplate.update("""
                INSERT INTO users VALUES (
                    50, 'legacyuser', 'secret123', 'Legacy', 'User',
                    TIMESTAMP '2025-01-08 09:00:00', NULL, 1, 2
                )
                """);
        try {
            assertThat(storedPassword("legacyuser")).isEqualTo("secret123");

            login("legacyuser", "secret123", "192.0.2.90");

            String upgraded = storedPassword("legacyuser");
            assertThat(upgraded).startsWith("pbkdf2-sha256$");
            assertThat(upgraded).doesNotContain("secret123");

            // The account still works, and a second login must not re-encode
            // what is already in the current format.
            login("legacyuser", "secret123", "192.0.2.91");
            assertThat(storedPassword("legacyuser")).isEqualTo(upgraded);

            performLogin("legacyuser", "wrong-password", "192.0.2.92")
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = 50");
        }
    }

    private String storedPassword(String login) {
        return jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE login = ?", String.class, login);
    }

    @Test
    void rejectsAttemptIdsOutsideTheRangeInsteadOfWrappingThemOntoRealOnes() throws Exception {
        String token = login("student", "secret123", "192.0.2.93");

        // 2^32 + 1 narrows to 1 when cast to int, which used to resolve to the
        // caller's attempt 1 — reading, and worse completing, an attempt other
        // than the one addressed.
        mockMvc.perform(get("/api/v1/attempts/4294967297")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/attempts/4294967297/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[1]}"))
                .andExpect(status().isBadRequest());

        // An id that fits but does not exist still reads as missing, not invalid.
        mockMvc.perform(get("/api/v1/attempts/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolvesRoleAndStatusWithoutAnOpenTransaction() {
        // Role and status are lazy, and Spring Security looks users up outside a
        // transaction during login. If the fetch join is ever dropped from
        // findByLogin, touching them here throws and the only symptom callers
        // see is a 401, which says nothing about the cause. This asserts the
        // associations are usable on a detached account.
        UserDetails student = userDetailsService.loadUserByUsername("student");
        assertThat(student.isEnabled()).isTrue();
        assertThat(student.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");

        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        // Status drives this flag, so a lazy status that failed to load would
        // not simply throw — it would silently mis-report the account.
        assertThat(userDetailsService.loadUserByUsername("blocked").isEnabled()).isFalse();
    }
}
