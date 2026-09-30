package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API documentation, turned off.
 *
 * <p>{@code HttpContractTest} asserts that it is public, which is the wanted
 * behaviour on a laptop and in CI. Both paths are permitted unauthenticated, so
 * wherever they are enabled the whole API surface — every route, schema and
 * validation constraint — is published to anything that can reach the port.
 * Whether that is wanted on a deployment is a deployment's call, and it used to
 * be a rebuild: {@code enabled: true} was written into application.yml as a
 * literal, the only pair of operational settings in that file not taken from the
 * environment. The cluster's ConfigMap now sets both to false.
 *
 * <p>Its own class rather than a case in HttpContractTest, because the switch is
 * read when the context is built. A second context is the cost of asserting that
 * the switch does anything at all, and asserting it is the point: a flag nothing
 * checks is a flag that stops working quietly.
 */
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        // Its own database, because it is its own context. application-test.yml
        // names one H2 instance and keeps it open for the JVM
        // (DB_CLOSE_DELAY=-1), so schema.sql runs again here against tables that
        // already exist — and the failure lands on whichever context loads
        // second, which is not this one. Any future test that varies a property
        // needs the same line.
        "spring.datasource.url=jdbc:h2:mem:quiz_api_docs_off;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocumentationSwitchTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesNoDocumentationWhenItIsSwitchedOff() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Resource was not found"));
        }
    }

    /** Switching the documentation off is not switching the API off. */
    @Test
    void stillServesTheApiItStoppedDescribing() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes/summary"))
                .andExpect(status().isOk());
    }
}
