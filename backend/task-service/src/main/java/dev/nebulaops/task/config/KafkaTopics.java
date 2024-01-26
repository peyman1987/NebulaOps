package dev.nebulaops.task.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {
    @Bean
    NewTopic taskEvents() {
        return TopicBuilder.name("nebula.task.events").partitions(3).replicas(1).build();
    }
}
