package com.miniproject.backend.tracker;

import java.util.List;

public record TicketTrackerView(
        String taskId,
        String title,
        String ticketType,
        List<TicketPhaseView> phases,
        String updatedAt) {
}
