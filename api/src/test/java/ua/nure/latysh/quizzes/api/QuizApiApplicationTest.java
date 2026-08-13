package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class QuizApiApplicationTest {
    @Test
    void delegatesStartupToSpringApplication() {
        try (var spring = mockStatic(SpringApplication.class)) {
            QuizApiApplication.main(new String[]{"--spring.main.web-application-type=none"});
            spring.verify(() -> SpringApplication.run(eq(QuizApiApplication.class),
                    eq(new String[]{"--spring.main.web-application-type=none"})));
        }
    }
}

