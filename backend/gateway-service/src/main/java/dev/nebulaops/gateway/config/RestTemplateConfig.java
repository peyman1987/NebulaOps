package dev.nebulaops.gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * v22.2 — RestTemplate config.
 *
 * Uses JdkClientHttpRequestFactory and relays the incoming Keycloak Bearer token
 * from the gateway to downstream Spring services so every microservice can act
 * as an OAuth2 resource server without breaking the existing proxy endpoints.
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.getInterceptors().add((request, body, execution) -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest currentRequest = attrs.getRequest();
                String authorization = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()
                        && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
            return execution.execute(request, body);
        });
        return template;
    }
}
