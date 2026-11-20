package com.mabini.parking.log;

import com.mabini.parking.log.dto.DeniedResponse;
import com.mabini.parking.log.dto.LogResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LogService {

    private static final int MAX_LIMIT = 200;

    private final AccessLogRepository logs;
    private final DeniedAttemptRepository denied;

    public LogService(AccessLogRepository logs, DeniedAttemptRepository denied) {
        this.logs = logs;
        this.denied = denied;
    }

    /** A user's own log book: only visits of their own vehicle. */
    @Transactional(readOnly = true)
    public List<LogResponse> mine(Long userId, int limit) {
        return logs.findByOwner(userId, PageRequest.of(0, clamp(limit))).stream()
                .map(LogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogResponse> recent(int limit) {
        return logs.findAllByOrderByEntryTimeDesc(PageRequest.of(0, clamp(limit))).stream()
                .map(LogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeniedResponse> recentDenied(int limit) {
        return denied.findAllByOrderByAttemptedAtDesc(PageRequest.of(0, clamp(limit))).stream()
                .map(DeniedResponse::from)
                .toList();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
