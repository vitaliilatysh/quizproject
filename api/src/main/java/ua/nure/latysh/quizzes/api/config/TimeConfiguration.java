package ua.nure.latysh.quizzes.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The shared UTC time source for application timestamps and deadlines. */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
