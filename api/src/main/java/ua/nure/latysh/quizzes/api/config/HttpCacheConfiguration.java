package ua.nure.latysh.quizzes.api.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class HttpCacheConfiguration {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> publicQuizEtagFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);

        FilterRegistrationBean<ShallowEtagHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("publicQuizEtagFilter");
        registration.addUrlPatterns("/api/v1/quizzes", "/api/v1/quizzes/*");
        return registration;
    }
}
