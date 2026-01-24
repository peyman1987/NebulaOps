package dev.nebulaops.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
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

    private void copy(HttpServletRequest currentRequest, HttpHeaders target, String header) {
        String value = currentRequest.getHeader(header);
        if (value != null && !value.isBlank() && !target.containsKey(header)) {
            target.set(header, value);
        }
    }
}
