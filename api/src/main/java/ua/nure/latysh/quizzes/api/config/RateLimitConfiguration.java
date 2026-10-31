package ua.nure.latysh.quizzes.api.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import ua.nure.latysh.quizzes.api.security.InMemoryRateLimitService;
import ua.nure.latysh.quizzes.api.security.RateLimitService;
import ua.nure.latysh.quizzes.api.security.RedisRateLimitService;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {
    @Bean
    RateLimitService rateLimitService(SecurityProperties properties,
                                     ObjectProvider<StringRedisTemplate> redisTemplate) {
        // Resolve Redis only in Redis mode; never fall back to per-instance limits.
        return switch (properties.rateLimit().backend()) {
            case MEMORY -> new InMemoryRateLimitService(properties);
            case REDIS -> new RedisRateLimitService(redisTemplate.getObject());
        };
    }
}
