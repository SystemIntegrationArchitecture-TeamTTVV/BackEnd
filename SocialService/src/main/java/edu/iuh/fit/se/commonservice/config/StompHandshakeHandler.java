package edu.iuh.fit.se.commonservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Custom handshake handler that creates a Principal from the username
 * stored in session attributes by WebSocketAuthInterceptor.
 * This ensures STOMP's convertAndSendToUser() works correctly.
 */
@Slf4j
@Component
public class StompHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String username = (String) attributes.get("username");
        if (username != null && !username.isBlank()) {
            log.debug("STOMP principal set to: {}", username);
            return () -> username;
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}
