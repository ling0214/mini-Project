package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Google OAuth 2.0 (authorization code flow) for Calendar + Gmail, read-only.
 * One connection covers both scopes, and a Meet event's join link lives on
 * the Calendar event itself (there is no separate "list my Meet meetings"
 * API for personal use), so this also serves the "Google Meet" platform
 * tile without a third scope.
 *
 * Single-operator local tool: one Google connection total, not per user —
 * see GoogleTokenEntity. The in-memory `state` field is a CSRF nonce for the
 * one authorize-redirect in flight; fine for a single local user, would need
 * per-session storage in a real multi-user deployment.
 */
@Component
public class GoogleConnector {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json;
    private final GoogleTokenRepository tokenRepository;
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String scopes;

    private final AtomicReference<String> pendingState = new AtomicReference<>();

    public GoogleConnector(
            ObjectMapper json,
            GoogleTokenRepository tokenRepository,
            @Value("${integrations.google.enabled:false}") boolean enabled,
            @Value("${integrations.google.client-id:}") String clientId,
            @Value("${integrations.google.client-secret:}") String clientSecret,
            @Value("${integrations.google.redirect-uri:http://localhost:8080/api/integrations/google/callback}") String redirectUri,
            @Value("${integrations.google.scopes:https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/gmail.readonly}") String scopes) {
        this.json = json;
        this.tokenRepository = tokenRepository;
        this.enabled = enabled;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri;
        this.scopes = scopes;
    }

    public boolean isConfigured() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank();
    }

    public boolean isConnected() {
        return tokenRepository.findById(GoogleTokenEntity.SINGLETON_ID).isPresent();
    }

    public String buildAuthorizationUrl() {
        if (!isConfigured()) {
            throw new ExternalConnectorException(
                    "Google connector is not configured. Set integrations.google.* values (client-id, client-secret) to enable it.");
        }
        String state = randomState();
        pendingState.set(state);
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(scopes)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&include_granted_scopes=true"
                + "&state=" + encode(state);
    }

    public void handleCallback(String code, String state) {
        if (!isConfigured()) {
            throw new ExternalConnectorException("Google connector is not configured.");
        }
        String expected = pendingState.getAndSet(null);
        if (expected == null || !expected.equals(state)) {
            throw new IllegalArgumentException("Google OAuth state did not match — start the connect flow again.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Google did not return an authorization code.");
        }

        try {
            String form = "code=" + encode(code)
                    + "&client_id=" + encode(clientId)
                    + "&client_secret=" + encode(clientSecret)
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&grant_type=authorization_code";
            exchangeAndStore(form);
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to exchange Google authorization code", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while exchanging Google authorization code", e);
        }
    }

    /** Returns a currently-valid access token, refreshing it first if it has expired. */
    public Optional<String> validAccessToken() {
        Optional<GoogleTokenEntity> stored = tokenRepository.findById(GoogleTokenEntity.SINGLETON_ID);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        GoogleTokenEntity entity = stored.get();
        if (entity.getExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return Optional.of(entity.getAccessToken());
        }
        if (entity.getRefreshToken() == null || entity.getRefreshToken().isBlank()) {
            return Optional.empty();
        }
        try {
            String form = "refresh_token=" + encode(entity.getRefreshToken())
                    + "&client_id=" + encode(clientId)
                    + "&client_secret=" + encode(clientSecret)
                    + "&grant_type=refresh_token";
            GoogleTokenEntity refreshed = exchangeAndStore(form);
            return Optional.of(refreshed.getAccessToken());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private GoogleTokenEntity exchangeAndStore(String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ExternalConnectorException(
                    "Google token endpoint returned HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode parsed = json.readTree(response.body());
        String accessToken = parsed.path("access_token").asText("");
        String refreshToken = parsed.path("refresh_token").asText(null);
        int expiresIn = parsed.path("expires_in").asInt(3600);
        String grantedScope = parsed.path("scope").asText(scopes);

        return tokenRepository.findById(GoogleTokenEntity.SINGLETON_ID)
                .map(existing -> {
                    existing.update(accessToken, refreshToken, Instant.now().plusSeconds(expiresIn), grantedScope);
                    return tokenRepository.save(existing);
                })
                .orElseGet(() -> tokenRepository.save(new GoogleTokenEntity(
                        accessToken, refreshToken, Instant.now().plusSeconds(expiresIn), grantedScope)));
    }

    /** Next upcoming events on the primary calendar — a Meet link, if the event has one, rides along as meetLink. */
    public List<GoogleCalendarEvent> listUpcomingEvents(int maxResults) {
        String token = requireValidToken();
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
                + "?timeMin=" + encode(Instant.now().toString())
                + "&maxResults=" + maxResults
                + "&singleEvents=true&orderBy=startTime";
        JsonNode response = getJson(url, token);

        List<GoogleCalendarEvent> events = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            String title = item.path("summary").asText("(no title)");
            JsonNode start = item.path("start");
            String startTime = start.path("dateTime").asText(start.path("date").asText(""));
            boolean recentlyUpdated = isWithinLastDay(item.path("updated").asText(""));
            events.add(new GoogleCalendarEvent(
                    item.path("id").asText(""),
                    title,
                    startTime,
                    joinAttendees(item.path("attendees")),
                    item.path("hangoutLink").asText(""),
                    recentlyUpdated));
        }
        return events;
    }

    /** Just the unread count (one cheap label lookup) — not per-message detail, that would be N+1 API calls for little payoff here. */
    public GmailSummary gmailSummary() {
        String token = requireValidToken();
        JsonNode response = getJson("https://www.googleapis.com/gmail/v1/users/me/labels/UNREAD", token);
        return new GmailSummary(response.path("messagesUnread").asInt(0));
    }

    private String requireValidToken() {
        return validAccessToken().orElseThrow(() -> new ExternalConnectorException(
                "Google is not connected yet — use /api/integrations/google/connect first."));
    }

    private JsonNode getJson(String url, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExternalConnectorException(
                        "Google API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to call Google API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while calling Google API", e);
        }
    }

    private static boolean isWithinLastDay(String rfc3339Timestamp) {
        if (rfc3339Timestamp == null || rfc3339Timestamp.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(rfc3339Timestamp).isAfter(Instant.now().minusSeconds(86_400));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String joinAttendees(JsonNode attendees) {
        if (!attendees.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode attendee : attendees) {
            if (attendee.path("self").asBoolean(false)) {
                continue;
            }
            String name = attendee.path("displayName").asText(attendee.path("email").asText(""));
            if (!name.isBlank()) {
                names.add(name);
            }
            if (names.size() == 3) {
                break;
            }
        }
        return String.join(", ", names);
    }

    public record GoogleCalendarEvent(
            String id, String title, String startTime, String attendees, String meetLink, boolean recentlyUpdated) {
    }

    public record GmailSummary(int unreadCount) {
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
