package com.miniproject.backend.web;

import com.miniproject.backend.skills.TestScopeReviewResult;

import java.util.List;

public record TestScopeReviewRequest(
        String profile,
        List<TestScopeReviewResult.ManagedTestCase> cases,
        String notes) {
}
