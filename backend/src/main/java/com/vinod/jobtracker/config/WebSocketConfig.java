package com.vinod.jobtracker.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket. Each signed-in browser subscribes to "/user/queue/updates"
 * and receives only its own user's events.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties props;
    private final StompAuthInterceptor stompAuthInterceptor;
    private final TaskScheduler brokerScheduler;

    public WebSocketConfig(AppProperties props, StompAuthInterceptor stompAuthInterceptor,
                           @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerScheduler) {
        this.props = props;
        this.stompAuthInterceptor = stompAuthInterceptor;
        this.brokerScheduler = brokerScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.frontendUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Heartbeats keep the connection alive through proxies that close idle sockets
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{20000, 20000})
                .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }

    /**
     * Separate scheduler for @Scheduled jobs so they don't share threads with the WebSocket broker.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("jobs-");
        return scheduler;
    }
}
