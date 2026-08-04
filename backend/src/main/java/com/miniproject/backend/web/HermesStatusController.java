package com.miniproject.backend.web;

import com.miniproject.backend.integrations.HermesStatusService;
import com.miniproject.backend.integrations.HermesStatusUpdateRequest;
import com.miniproject.backend.integrations.HermesStatusView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound side of the Hermes bridge — Hermes calls POST /status to report
 * progress on a task the analyst workbench previously handed off (see
 * ExternalHandoffService "hermes" destination for the outbound side). No
 * auth on this endpoint yet (matches the rest of this dev-only backend);
 * add integrations.hermes.status-token validation here if that's needed later.
 */
@RestController
@RequestMapping("/api/hermes")
public class HermesStatusController {

    private final HermesStatusService service;

    public HermesStatusController(HermesStatusService service) {
        this.service = service;
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/status")
    public HermesStatusView recordStatus(@RequestBody HermesStatusUpdateRequest request) {
        return service.recordStatus(
                request.sourceTaskId(), request.status(), request.note(), request.project(), request.similarIssues());
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/status")
    public List<HermesStatusView> statusHistory(@RequestParam("source_task_id") String sourceTaskId) {
        return service.history(sourceTaskId);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/status/current")
    public List<HermesStatusView> currentStatuses(
            @RequestParam(value = "project", required = false) String project) {
        return service.currentForAllTasks(project);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
