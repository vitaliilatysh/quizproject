package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void listsQuizzesAndReturnsOneById() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes"))
                .andExpect(status().isOk())
                .andExpect(he…3267 tokens truncated…7, 'secret123', 1, 1);
INSERT INTO subjects VALUES (1, 'Java Basics'), (2, 'Collections');
INSERT INTO levels VALUES (1, 'low'), (2, 'medium');
INSERT INTO quizzes VALUES
  (1, 'Java syntax', 5, 1, 1),
  (2, 'Lists', 10, 2, 2);
INSERT INTO questions VALUES
  (1, 'Question 1', 1),
  (2, 'Question 2', 1);
INSERT INTO attempts VALUES
  (1, 80, TIMESTAMP '2026-08-12 10:15:30', TRUE, 1, 1),
  (2, 20, NULL, FALSE, 2, 1);
