package com.miniproject.backend.web;

public record SkillApproveRequest(
    String approvedBy,
    String shareScope,
    String approverNotes,
    Boolean force
) {
    public boolean forceOrDefault() {
        return force != null && force;
    }
}
