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

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
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

    /** A downloadable table (CSV) of every vehicle that parked between two dates. Dates are optional: default is the last 30 days. */
    @GetMapping("/manager/logs/export")
    @PreAuthorize(Roles.STAFF)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] file = logService.exportCsv(from, to);
        String name = "parking-log" + (from != null ? "-from-" + from : "") + (to != null ? "-to-" + to : "") + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(file);
    }

    @GetMapping("/manager/denied")
    @PreAuthorize(Roles.STAFF)
    public List<DeniedResponse> denied(@RequestParam(defaultValue = "50") int limit) {
        return logService.recentDenied(limit);
    }
}
