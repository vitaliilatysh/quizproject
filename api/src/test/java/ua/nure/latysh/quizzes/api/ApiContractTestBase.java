package ua.nure.latysh.quizzes.api;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import ua.nure.latysh.quizzes.api.attempt.AttemptService;
import ua.nure.latysh.quizzes.api.quiz.QuizQueryService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What every contract test needs: the application, a MockMvc against it, and
 * the few helpers that sign a reader in and read an id back out of a response.
 *
 * <p>Split out of one 1652-line class that held all thirty-seven of them and
 * touched all six areas of the API at once. The subclasses carry the same
 * annotations, so Spring hands them the same cached context and the suite
 * starts the application no more often than it did before.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiContractTestBase {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserDetailsService userDetailsService;

    @Autowired
    protected AttemptService attemptService;

    @Autowired
    protected QuizQueryService quizQueryService;

    @Autowired
    protected org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /**
     * Sends the request from a given address. The rate limiter buckets by
     * client address, so a test that issues extra requests from the default one
     * spends budget the other tests are counting on.
     */
    protected static RequestPostProcessor from(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    protected String login(String username, String password, String remoteAddress) throws Exception {
        return loginTokens(username, password, remoteAddress).accessToken();
    }

    protected Tokens loginTokens(String username, String password, String remoteAddress) throws Exception {
        MvcResult result = performLogin(username, password, remoteAddress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        var response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Tokens(
                response.get("accessToken").asString(),
                response.get("refreshToken").asString());
    }

    protected org.springframework.test.web.servlet.ResultActions startAttempt(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    protected long attemptId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("attemptId").asLong();
    }

    protected int responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    protected org.springframework.test.web.servlet.ResultActions performLogin(
            String username, String password, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String refreshBody(String token) {
        return "{\"refreshToken\":\"%s\"}".formatted(token);
    }

    protected record Tokens(String accessToken, String refreshToken) {
    }

    // Each of these takes its own client address: the catalogue shares one
    // rate-limit bucket per IP, and draining the default one fails whichever
    // test happens to run afterwards.
    protected MockHttpServletRequestBuilder catalogue(String remoteAddress) {
        return get("/api/v1/quizzes").with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        });
    }

    protected String storedPassword(String login) {
        return jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE login = ?", String.class, login);
    }
}
