package com.mabini.parking.demo;

import com.mabini.parking.common.Roles;
import com.mabini.parking.config.OffsetClock;
import com.mabini.parking.penalty.PenaltyService;
import com.mabini.parking.penalty.dto.CheckResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * DEMO ONLY. This controller does not exist at all unless app.demo.time-control=true, so on a
 * normal run these URLs answer "not found". It lets the super admin move the clock forward so
 * a no-show or an overstay can be shown in seconds instead of hours.
 */
@RestController
@RequestMapping("/api/admin/demo")
@ConditionalOnProperty(name = "app.demo.time-control", havingValue = "true")
public class DemoController {

    public record TimeState(LocalDateTime now, long offsetMinutes) {
    }

    public record AdvanceRequest(
            @NotNull(message = "Minutes are required.")
            @Min(value = 1, message = "Move forward by at least 1 minute.")
            @Max(value = 10080, message = "Move forward by at most 7 days at a time.")
            Integer minutes) {
    }

    private final OffsetClock clock;
    private final PenaltyService penaltyService;

    public DemoController(OffsetClock clock, PenaltyService penaltyService) {
        this.clock = clock;
        this.penaltyService = penaltyService;
    }

    @GetMapping("/time")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public TimeState time() {
        return state();
    }

    @PostMapping("/time/advance")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public TimeState advance(@Valid @RequestBody AdvanceRequest request) {
        clock.advance(Duration.ofMinutes(request.minutes()));
        return state();
    }

    @PostMapping("/time/reset")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public TimeState reset() {
        clock.reset();
        return state();
    }

    /** Runs the no-show / overstay check right now instead of waiting for the timer. */
    @PostMapping("/run-penalty-check")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public CheckResult runPenaltyCheck() {
        return penaltyService.runChecks();
    }

    private TimeState state() {
        return new TimeState(LocalDateTime.now(clock), clock.getOffset().toMinutes());
    }
}
