package dev.nebulaops.mvc;

import dev.nebulaops.mvc.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SpringMvcServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringMvcServiceApplication.class, args);
    }
}
