package ua.nure.latysh.quizzes.api.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import ua.nure.latysh.quizzes.api.support.ApiError;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiErrorWriterTest {
    @Test
    void timestampsSecurityErrorsWithTheProvidedClock() throws Exception {
        Instant now = Instant.parse("2030-02-03T04:05:06Z");
        var mapper = mock(ObjectMapper.class);
        var request = new MockHttpServletRequest("GET", "/api/v1/profile");
        var response = new MockHttpServletResponse();
        var writer = new ApiErrorWriter(mapper, Clock.fixed(now, ZoneOffset.UTC));

        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in required");

        var error = ArgumentCaptor.forClass(ApiError.class);
        verify(mapper).writeValue(any(java.io.OutputStream.class), error.capture());
        assertThat(error.getValue().timestamp()).isEqualTo(now);
        assertThat(error.getValue().path()).isEqualTo("/api/v1/profile");
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
