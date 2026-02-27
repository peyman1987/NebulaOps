package dev.nebulaops.aiops;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class RestClientConfig {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        RestTemplate template = new RestTemplate(requestFactory);
        template.getInterceptors().add((request, body, execution) -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest current = attrs.getRequest();
                copy(current, request.getHeaders(), HttpHeaders.AUTHORIZATION);
                copy(current, request.getHeaders(), "X-Correlation-Id");
                copy(current, request.getHeaders(), "traceparent");
            }
            return execution.execute(request, body);
        });
        return template;
    }

    private void copy(HttpServletRequest current, HttpHeaders target, String header) {
        String value = current.getHeader(header);
        if (value != null && !value.isBlank() && !target.containsKey(header)) {
            target.set(header, value);
        }
    }
}
