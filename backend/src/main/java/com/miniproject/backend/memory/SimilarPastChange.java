package com.miniproject.backend.memory;

/** One past reviewed artifact whose memory card overlapped the current ticket text. */
public record SimilarPastChange(String taskId, String summary, int score, String reviewedAt) {
}
