package com.miniproject.backend.web;

import com.miniproject.backend.integrations.JiraTicketImportRequest;
import com.miniproject.backend.integrations.JiraTicketImportResponse;
import com.miniproject.backend.integrations.JiraTicketImportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final JiraTicketImportService jiraTicketImportService;

    public IntegrationController(JiraTicketImportService jiraTicketImportService) {
        this.jiraTicketImportService = jiraTicketImportService;
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/jira/import")
    public JiraTicketImportResponse importJiraTicket(@RequestBody JiraTicketImportRequest request) {
        return jiraTicketImportService.importTicket(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
