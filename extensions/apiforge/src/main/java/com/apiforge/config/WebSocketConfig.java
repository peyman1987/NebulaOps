package com.apiforge.config;

import com.apiforge.websocket.ProxySocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ProxySocketHandler h;

    public WebSocketConfig(ProxySocketHandler h) {
        this.h = h;
    }

    public void registerWebSocketHandlers(WebSocketHandlerRegistry r) {
        r.addHandler(h, "/ws/proxy").setAllowedOrigins("*");
    }
}
