package com.miniproject.backend.web;

import com.miniproject.backend.integrations.CsvTicketImportRequest;
import com.miniproject.backend.integrations.CsvTicketImportService;
import com.miniproject.backend.integrations.GoogleConnector;
import com.miniproject.backend.integrations.JiraConnector;
import com.miniproject.backend.integrations.JiraTicketImportRequest;
import com.miniproject.backend.integrations.JiraTicketImportResponse;
import com.miniproject.backend.integrations.JiraTicketImportService;
import com.miniproject.backend.integrations.ExternalConnectorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final JiraTicketImportService jiraTicketImportService;
    private final JiraConnector jiraConnector;
    private final CsvTicketImportService csvTicketImportService;
    private final GoogleConnector googleConnector;
    private final String googleFrontendRedirect;

    public IntegrationController(
            JiraTicketImportService jiraTicketImportService,
            JiraConnector jiraConnector,
            CsvTicketImportService csvTicketImportService,
            GoogleConnector googleConnector,
            @Value("${integrations.google.frontend-redirect:http://127.0.0.1:5173/}") String googleFrontendRedirect) {
        this.jiraTicketImportService = jiraTicketImportService;
        this.jiraConnector = jiraConnector;
        this.csvTicketImportService = csvTicketImportService;
        this.googleConnector = googleConnector;
        this.googleFrontendRedirect = googleFrontendRedirect;
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/jira/import")
    public JiraTicketImportResponse importJiraTicket(@RequestBody JiraTicketImportRequest request) {
        return jiraTicketImportService.importTicket(request);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/csv/import")
    public JiraTicketImportResponse importCsvTicket(@RequestBody CsvTicketImportRequest request) {
        return csvTicketImportService.importTicket(request);
    }

    /** Diagnostic only: confirms which Atlassian account the configured Jira token authenticates as. */
    @CrossOrigin(origins = "*")
    @GetMapping("/jira/whoami")
    public JiraConnector.JiraIdentity jiraWhoAmI() {
        return jiraConnector.whoAmI();
    }

    /**
     * Real browser navigation, not a fetch/XHR call — the frontend "Connect"
     * button sets window.location.href here directly, so no @CrossOrigin
     * (CORS governs cross-origin fetch/XHR, not top-level navigation).
     */
    @GetMapping("/google/connect")
    public ResponseEntity<Void> connectGoogle() {
        String authorizeUrl = googleConnector.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(authorizeUrl)).build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            throw new ExternalConnectorException("Google declined the connection request: " + error);
        }
        googleConnector.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(googleFrontendRedirect)).build();
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/google/status")
    public GoogleStatus googleStatus() {
        return new GoogleStatus(googleConnector.isConfigured(), googleConnector.isConnected());
    }

    public record GoogleStatus(boolean configured, boolean connected) {
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/google/calendar/events")
    public List<GoogleConnector.GoogleCalendarEvent> googleCalendarEvents() {
        return googleConnector.listUpcomingEvents(10);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/google/gmail/summary")
    public GoogleConnector.GmailSummary googleGmailSummary() {
        return googleConnector.gmailSummary();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(ExternalConnectorException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleConnectorError(ExternalConnectorException e) {
        return e.getMessage();
    }
}
