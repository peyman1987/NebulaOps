package dev.nebulaops.task.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String TASK_EXCHANGE = "nebula.task.exchange";
    public static final String TASK_EVENTS_QUEUE = "nebula.task.events";
    public static final String TASK_ROUTING_KEY = "task.event";

    @Bean
    DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    Queue taskEventsQueue() {
        return new Queue(TASK_EVENTS_QUEUE, true);
    }

    @Bean
    Binding taskEventsBinding(Queue taskEventsQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(taskEventsQueue).to(taskExchange).with(TASK_ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
