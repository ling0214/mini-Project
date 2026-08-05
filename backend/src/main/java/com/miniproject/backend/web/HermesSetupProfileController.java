package com.miniproject.backend.web;

import com.miniproject.backend.integrations.HermesSetupProfileService;
import com.miniproject.backend.integrations.HermesSetupProfileView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/hermes/setup-profiles")
public class HermesSetupProfileController {

    private final HermesSetupProfileService service;

    public HermesSetupProfileController(HermesSetupProfileService service) {
        this.service = service;
    }

    @CrossOrigin(origins = "*")
    @PostMapping
    public HermesSetupProfileView save(@RequestBody HermesSetupProfileRequest request) {
        return service.save(request.id(), request.name(), new HermesSetupProfileService.HermesSetupProfileSaveRequest(
                request.repoPath(), request.platforms(), request.discordChannelId(), request.emailImapHost(),
                request.emailAccount(), request.emailAllowedSenders(), request.incidentReportsDir(),
                request.incidentExtractsDir(), request.incidentDownloadsDir(), request.serverLogPath(),
                request.prPackageEnabled(), request.gitHost()));
    }

    @CrossOrigin(origins = "*")
    @GetMapping
    public List<HermesSetupProfileView> list() {
        return service.listAll();
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/{id}")
    public HermesSetupProfileView get(@PathVariable String id) {
        return service.get(id).orElseThrow(() -> new NoSuchElementException("No setup profile found for id " + id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
