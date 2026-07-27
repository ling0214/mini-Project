package com.miniproject.backend.web;

import java.util.List;

public record HandoffSummaryRequest(String profile, String requirementTaskId, List<String> testTaskIds) {
}
