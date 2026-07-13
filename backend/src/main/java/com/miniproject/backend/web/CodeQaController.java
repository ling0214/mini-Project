package com.miniproject.backend.web;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.coordinator.CoordinatorService;
import com.miniproject.backend.skills.CodeQaResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class CodeQaController {

    private final CoordinatorService coordinator;

    public CodeQaController(CoordinatorService coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/code-qa")
    public Artifact<CodeQaResult> codeQa(@RequestBody CodeQaRequest request) {
        if (request.profile() == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("profile and question are required");
        }
        return coordinator.codeQa(request.profile(), request.question());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
