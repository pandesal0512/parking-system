package com.mabini.parking.log;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.common.Roles;
import com.mabini.parking.log.dto.DeniedResponse;
import com.mabini.parking.log.dto.LogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /** The user's own log book. */
    @GetMapping("/logs/me")
    @PreAuthorize(Roles.USER_ONLY)
    public List<LogResponse> mine(@AuthenticationPrincipal AuthUser current,
                                  @RequestParam(defaultValue = "50") int limit) {
        return logService.mine(current.id(), limit);
    }

    @GetMapping("/manager/logs")
    @PreAuthorize(Roles.STAFF)
    public List<LogResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return logService.recent(limit);
    }

    @GetMapping("/manager/denied")
    @PreAuthorize(Roles.STAFF)
    public List<DeniedResponse> denied(@RequestParam(defaultValue = "50") int limit) {
        return logService.recentDenied(limit);
    }
}
