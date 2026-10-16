package com.vinod.jobtracker.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.vinod.jobtracker.config.SecurityConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Minimal Gmail REST API client (read-only), using Spring's RestClient.
 * Only message metadata (From, Subject, date) and Gmail's short preview are requested.
 */
@Component
public class GmailClient {

    private static final String API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final RestClient rest;
    private final ClientRegistrationRepository clientRegistrations;

    public GmailClient(RestClient rest, ClientRegistrationRepository clientRegistrations) {
        this.rest = rest;
        this.clientRegistrations = clientRegistrations;
    }

    public record GmailMessage(String id, String from, String subject, String snippet, Instant receivedAt) {
    }

    /** Thrown when Google says the refresh token is no longer valid (user revoked access). */
    public static class GmailAuthException extends RuntimeException {
        public GmailAuthException(String message) {
            super(message);
        }
    }

    /** Exchanges the stored refresh token for a short-lived access token. */
    public String refreshAccessToken(String refreshToken) {
        ClientRegistration registration = clientRegistrations.findByRegistrationId(SecurityConfig.GMAIL_REGISTRATION);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            JsonNode body = rest.post()
                    .uri(registration.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("access_token")) {
                throw new GmailAuthException("Google did not return an access token");
            }
            return body.get("access_token").asText();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new GmailAuthException("Gmail access was revoked or expired");
            }
            throw e;
        }
    }

    public List<String> listMessageIds(String accessToken, String query, int max) {
        JsonNode body = rest.get()
                .uri(API + "/messages?q={q}&maxResults={max}", query, max)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);
        List<String> ids = new ArrayList<>();
        if (body != null && body.has("messages")) {
            body.get("messages").forEach(m -> ids.add(m.get("id").asText()));
        }
        return ids;
    }

    public GmailMessage getMessage(String accessToken, String id) {
        JsonNode body = rest.get()
                .uri(API + "/messages/{id}?format=metadata&metadataHeaders=From&metadataHeaders=Subject", id)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            return new GmailMessage(id, "", "", "", Instant.now());
        }
        String from = "";
        String subject = "";
        for (JsonNode header : body.path("payload").path("headers")) {
            String name = header.path("name").asText();
            if ("From".equalsIgnoreCase(name)) {
                from = header.path("value").asText();
            } else if ("Subject".equalsIgnoreCase(name)) {
                subject = header.path("value").asText();
            }
        }
        long internalDate = body.path("internalDate").asLong(System.currentTimeMillis());
        return new GmailMessage(id, from, subject, unescape(body.path("snippet").asText("")),
                Instant.ofEpochMilli(internalDate));
    }

    public void revoke(String refreshToken) {
        try {
            rest.post().uri("https://oauth2.googleapis.com/revoke?token={t}", refreshToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Best effort: the token may already be revoked
        }
    }

    /** Gmail snippets are HTML-escaped ("&#39;", "&amp;"). */
    private static String unescape(String s) {
        return s.replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }
}
