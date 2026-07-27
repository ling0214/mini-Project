package com.miniproject.backend.github;

/** Wraps any failure reaching or parsing the GitHub REST API. */
public class GitHubPrException extends RuntimeException {

    public GitHubPrException(String message) {
        super(message);
    }

    public GitHubPrException(String message, Throwable cause) {
        super(message, cause);
    }
}
