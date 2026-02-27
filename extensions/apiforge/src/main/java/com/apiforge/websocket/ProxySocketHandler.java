package com.apiforge.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ProxySocketHandler extends TextWebSocketHandler {
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        s.sendMessage(new TextMessage("APIForge WebSocket proxy ready. For browser-native sockets use the WebSocket panel directly."));
    }

    protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
        s.sendMessage(new TextMessage("echo: " + m.getPayload()));
    }
}
