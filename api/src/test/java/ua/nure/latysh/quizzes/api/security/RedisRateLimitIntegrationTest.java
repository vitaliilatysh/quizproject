package ua.nure.latysh.quizzes.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisRateLimitIntegrationTest {
    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.8-alpine"))
            .withExposedPorts(6379);

    @Test
    void sharesAnAtomicLimitAcrossApplicationInstances() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            RateLimitService firstInstance = new RedisRateLimitService(template);
            RateLimitService secondInstance = new RedisRateLimitService(template);

            assertTrue(firstInstance.acquire("auth:203.0.113.8", 2, Duration.ofMinutes(1)).allowed());
            assertTrue(secondInstance.acquire("auth:203.0.113.8", 2, Duration.ofMinutes(1)).allowed());
            assertFalse(firstInstance.acquire("auth:203.0.113.8", 2, Duration.ofMinutes(1)).allowed());
        } finally {
            connectionFactory.destroy();
        }
    }
}
