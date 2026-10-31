package ua.nure.latysh.quizzes.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import ua.nure.latysh.quizzes.api.security.InMemoryRateLimitService;
import ua.nure.latysh.quizzes.api.security.RateLimitService;
import ua.nure.latysh.quizzes.api.security.RedisRateLimitService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RateLimitConfiguration.class, PropertiesConfiguration.class)
            .withPropertyValues("JWT_SECRET=test-secret");

    @Test
    void memoryModeNeedsNoRedisBean() {
        contextRunner.withPropertyValues("RATE_LIMIT_BACKEND=memory").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RateLimitService.class)
                    .doesNotHaveBean(StringRedisTemplate.class);
            assertThat(context.getBean(RateLimitService.class)).isInstanceOf(InMemoryRateLimitService.class);
        });
    }

    @Test
    void redisModeSelectsExactlyOneService() {
        contextRunner.withPropertyValues("RATE_LIMIT_BACKEND=redis")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RateLimitService.class);
                    assertThat(context.getBean(RateLimitService.class)).isInstanceOf(RedisRateLimitService.class);
                });
    }

    @Test
    void applicationDefaultsToRedis() {
        contextRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RateLimitService.class);
                    assertThat(context.getBean(RateLimitService.class)).isInstanceOf(RedisRateLimitService.class);
                });
    }

    @Test
    void redisModeDoesNotFallBackWhenRedisBeanIsMissing() {
        contextRunner.withPropertyValues("RATE_LIMIT_BACKEND=redis").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        });
    }

    @Test
    void invalidBackendFailsStartup() {
        contextRunner.withPropertyValues("RATE_LIMIT_BACKEND=unknown").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesConfiguration {
    }
}
