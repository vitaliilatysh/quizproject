package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Export from the real Spring mappings, never from a hand-maintained schema. */
class OpenApiExportTest extends ApiContractTestBase {
    @Test
    void exportsTheApiContract() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn();
        Path output = Path.of("build", "openapi", "openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, response.getResponse().getContentAsString());
    }
}
