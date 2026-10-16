package com.vinod.jobtracker.config;

import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Browsers can't set headers on the WebSocket handshake, so the JWT is sent in the
 * STOMP CONNECT frame instead. We validate it here and attach the user id as the principal.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Missing token");
            }
            try {
                Jwt jwt = jwtDecoder.decode(header.substring(7));
                accessor.setUser(new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of()));
            } catch (JwtException e) {
                throw new MessagingException("Invalid token");
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessagingException("Subscription not allowed");
            }
        }
        return message;
    }
}
