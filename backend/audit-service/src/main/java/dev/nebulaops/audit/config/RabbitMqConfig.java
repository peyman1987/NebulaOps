package dev.nebulaops.audit.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class RabbitMqConfig {
    @Bean
    public Queue auditTaskEventsQueue(@Value("${nebulaops.audit.task-events-queue:nebula.audit.task.events}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    public TopicExchange taskExchange(@Value("${nebulaops.audit.task-exchange:nebula.task.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Binding taskAuditBinding(Queue auditTaskEventsQueue, TopicExchange taskExchange,
                                    @Value("${nebulaops.audit.task-routing-key:task.event}") String routingKey) {
        return BindingBuilder.bind(auditTaskEventsQueue).to(taskExchange).with(routingKey);
    }
}
