package com.vinod.jobtracker.realtime;

import java.time.Instant;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Pushes events to every browser/device the user has open, over WebSocket.
 * The frontend subscribes to "/user/queue/updates".
 */
@Service
public class LiveUpdateService {

    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String APPLICATION_UPDATED = "APPLICATION_UPDATED";
    public static final String APPLICATION_DELETED = "APPLICATION_DELETED";
    public static final String ACTIVITY = "ACTIVITY";
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String EMAILS_CHANGED = "EMAILS_CHANGED";
    public static final String GMAIL_STATUS = "GMAIL_STATUS";

    private final SimpMessagingTemplate messaging;

    public LiveUpdateService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void send(Long userId, String type, Object payload) {
        messaging.convertAndSendToUser(String.valueOf(userId), "/queue/updates",
                new LiveEvent(type, payload, Instant.now()));
    }

    public void notify(Long userId, String title, String message, String level) {
        send(userId, NOTIFICATION, Map.of("title", title, "message", message, "level", level));
    }

    public record LiveEvent(String type, Object payload, Instant at) {
    }
}
