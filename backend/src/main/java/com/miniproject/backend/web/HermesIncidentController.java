package com.miniproject.backend.web;

import com.miniproject.backend.integrations.HermesIncidentReader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Java port of Hermes's own plugins/incident-dashboard/dashboard/plugin_api.py
 * router (mounted there at /api/plugins/incident-dashboard/) -- same
 * incidents/*.json + agent-tasks/* files on disk, same field shapes, same
 * stop/continue/retry semantics, so mini-Project's Hermes Incident Tracker
 * page can show Hermes's real production-incident pipeline directly instead
 * of only the packages mini-Project itself handed off (see
 * HermesStatusController for that separate, narrower bridge).
 */
@RestController
@RequestMapping("/api/hermes/incidents")
public class HermesIncidentController {

    private final HermesIncidentReader reader;

    public HermesIncidentController(HermesIncidentReader reader) {
        this.reader = reader;
    }

    @CrossOrigin(origins = "*")
    @GetMapping
    public Map<String, Object> list(
            @RequestParam("hermes_home") String hermesHome,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return Map.of("incidents", reader.listIncidents(hermesHome, limit));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/{incidentKey}")
    public Map<String, Object> get(
            @RequestParam("hermes_home") String hermesHome,
            @PathVariable String incidentKey) {
        return reader.getIncident(hermesHome, incidentKey);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{incidentKey}/stop")
    public Map<String, Object> stop(
            @RequestParam("hermes_home") String hermesHome,
            @PathVariable String incidentKey) {
        return Map.of("ok", true, "incident", reader.stopIncident(hermesHome, incidentKey));
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{incidentKey}/continue")
    public Map<String, Object> continueIncident(
            @RequestParam("hermes_home") String hermesHome,
            @PathVariable String incidentKey) {
        return Map.of("ok", true, "incident", reader.continueIncident(hermesHome, incidentKey));
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{incidentKey}/retry")
    public Map<String, Object> retry(
            @RequestParam("hermes_home") String hermesHome,
            @PathVariable String incidentKey) {
        return Map.of("ok", true, "incident", reader.retryIncident(hermesHome, incidentKey));
    }

    @ExceptionHandler(HermesIncidentReader.IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(HermesIncidentReader.IncidentNotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(HermesIncidentReader.HermesIncidentException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleReadError(HermesIncidentReader.HermesIncidentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
