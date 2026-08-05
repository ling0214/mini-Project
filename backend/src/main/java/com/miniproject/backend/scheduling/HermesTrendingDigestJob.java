package com.miniproject.backend.scheduling;

import com.miniproject.backend.coordinator.CoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly, not daily -- GitHub Trending shifts slowly enough that a daily run
 * would mostly repeat itself and become noise the user starts ignoring (see
 * the mini-Project <-> Hermes implementation plan). Runs the exact same code
 * path as a manual trigger from the frontend (CoordinatorService.hermesTrendingDigest),
 * under the "software-analyst" profile since there's no logged-in user for a
 * background job to act as.
 */
@Component
public class HermesTrendingDigestJob {

    private static final Logger log = LoggerFactory.getLogger(HermesTrendingDigestJob.class);

    private final CoordinatorService coordinatorService;

    public HermesTrendingDigestJob(CoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
    }

    @Scheduled(cron = "${hermes.trending-digest.cron:0 0 8 * * MON}")
    public void run() {
        try {
            var artifact = coordinatorService.hermesTrendingDigest("software-analyst");
            log.info("Hermes trending digest completed: task_id={}", artifact.taskId());
        } catch (RuntimeException e) {
            log.warn("Hermes trending digest run failed (non-fatal, will retry next scheduled run)", e);
        }
    }
}
