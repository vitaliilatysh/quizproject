package ua.nure.latysh.quizzes.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI quizOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Quiz REST API").version("v1")
                        .description("Read-only API for quizzes and authenticated user results."))
                .components(new Components().addSecuritySchemes("basicAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")));
    }
}
