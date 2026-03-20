package dev.nebulaops.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate template = builder
                .setConnectTimeout(Duration.ofMillis(timeout("NEBULAOPS_HTTP_CONNECT_TIMEOUT_MS", 2500)))
                .setReadTimeout(Duration.ofMillis(timeout("NEBULAOPS_HTTP_READ_TIMEOUT_MS", 15000)))
                .build();
        template.getInterceptors().add((request, body, execution) -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest currentRequest = attrs.getRequest();
                copy(currentRequest, request.getHeaders(), HttpHeaders.AUTHORIZATION);
                copy(currentRequest, request.getHeaders(), CorrelationIdFilter.HEADER);
                copy(currentRequest, request.getHeaders(), "traceparent");
            }
            return execution.execute(request, body);
        });
        return template;
    }

    private long timeout(String key, long fallback) {
        try {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? fallback : Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void copy(HttpServletRequest currentRequest, HttpHeaders target, String header) {
        String value = currentRequest.getHeader(header);
        if (value != null && !value.isBlank() && !target.containsKey(header)) {
            target.set(header, value);
        }
    }
}
