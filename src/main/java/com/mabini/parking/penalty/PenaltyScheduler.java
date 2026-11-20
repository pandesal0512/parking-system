package com.mabini.parking.penalty;

import com.mabini.parking.penalty.dto.CheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Runs the no-show / overstay check on a timer. Turn off with app.penalty.job-enabled=false. */
@Component
@ConditionalOnProperty(name = "app.penalty.job-enabled", havingValue = "true", matchIfMissing = true)
public class PenaltyScheduler {

    private static final Logger log = LoggerFactory.getLogger(PenaltyScheduler.class);

    private final PenaltyService penaltyService;

    public PenaltyScheduler(PenaltyService penaltyService) {
        this.penaltyService = penaltyService;
    }

    @Scheduled(initialDelayString = "${app.penalty.check-seconds:60}",
            fixedDelayString = "${app.penalty.check-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void run() {
        try {
            CheckResult result = penaltyService.runChecks();
            if (result.noShows() > 0 || result.overstays() > 0) {
                log.info("Penalty check: {} no-show(s), {} overstay(s).", result.noShows(), result.overstays());
            }
        } catch (RuntimeException e) {
            // Never let one failure stop the timer.
            log.error("Penalty check failed", e);
        }
    }
}
