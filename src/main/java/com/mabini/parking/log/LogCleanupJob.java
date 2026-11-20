package com.mabini.parking.log;

import com.mabini.parking.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Deletes finished visits and denied attempts older than app.logs.retention-days (default 30).
 * Visits that are still open (vehicle inside) are never deleted. Runs daily at 03:00.
 */
@Component
@ConditionalOnProperty(name = "app.logs.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class LogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupJob.class);

    private final AccessLogRepository logs;
    private final DeniedAttemptRepository denied;
    private final AppProperties props;
    private final Clock clock;

    public LogCleanupJob(AccessLogRepository logs, DeniedAttemptRepository denied, AppProperties props, Clock clock) {
        this.logs = logs;
        this.denied = denied;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Manila")
    @Transactional
    public void run() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(props.logs().retentionDays());
        int visits = logs.deleteFinishedBefore(cutoff);
        int refused = denied.deleteBefore(cutoff);
        if (visits > 0 || refused > 0) {
            log.info("Log cleanup removed {} visit(s) and {} refused attempt(s) older than {}.", visits, refused, cutoff);
        }
    }
}
