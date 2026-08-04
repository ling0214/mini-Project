package com.miniproject.backend.web;

import com.miniproject.backend.tracker.TicketTrackerService;
import com.miniproject.backend.tracker.TicketTrackerView;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketTrackerController {

    private final TicketTrackerService service;

    public TicketTrackerController(TicketTrackerService service) {
        this.service = service;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/tracker")
    public List<TicketTrackerView> tracker(@RequestParam(value = "project", required = false) String project) {
        return service.listTickets(project);
    }
}
