package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        if (username != null && !username.isBlank()) {
            presenceService.markOnlineByUsername(username);
            log.debug("Presence online from websocket connect: {}", username);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        if (username != null && !username.isBlank()) {
            presenceService.markOfflineByUsername(username);
            log.debug("Presence offline from websocket disconnect: {}", username);
        }
    }
}
