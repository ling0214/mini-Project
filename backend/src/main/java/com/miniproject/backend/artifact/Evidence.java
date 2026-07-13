package com.miniproject.backend.artifact;

/** One grounded citation backing a claim in a skill's output. */
public record Evidence(String claim, String source) {
}
