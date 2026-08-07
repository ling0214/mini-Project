package com.miniproject.backend.web;

import com.miniproject.backend.integrations.GitLogReader;
import com.miniproject.backend.integrations.HermesVersionControlService;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.Map;

@RestController
@RequestMapping("/api/hermes/version-control")
public class HermesVersionControlController {

    private final HermesVersionControlService service;
    private final String agentRepoPath;

    public HermesVersionControlController(
            HermesVersionControlService service,
            @Value("${integrations.hermes.agent-repo-path}") String agentRepoPath) {
        this.service = service;
        this.agentRepoPath = agentRepoPath;
    }

    /** The one Hermes upstream clone -- see integrations.hermes.agent-repo-path. */
    @CrossOrigin(origins = "*")
    @GetMapping("/default-repo-path")
    public Map<String, String> defaultRepoPath() {
        return Map.of("repo_path", agentRepoPath);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/status")
    public HermesVersionControlService.HermesPullStatusView status(@RequestParam("repo_path") String repoPath) {
        return service.checkStatus(repoPath);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/pull")
    public HermesVersionControlService.HermesPullResultView pull(@RequestBody HermesPullRequest request) {
        if (request.sourceTaskId() == null || request.sourceTaskId().isBlank()
                || request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("sourceTaskId and repoPath are required");
        }
        return service.pull(request.sourceTaskId(), request.repoPath());
    }

    public record HermesPullRequest(String sourceTaskId, String repoPath) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(GitLogReader.GitLogReaderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleGitError(GitLogReader.GitLogReaderException e) {
        return e.getMessage();
    }
}
