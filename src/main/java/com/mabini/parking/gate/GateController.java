package com.mabini.parking.gate;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.common.Roles;
import com.mabini.parking.gate.dto.GateRequest;
import com.mabini.parking.gate.dto.GateResult;
import com.mabini.parking.gate.dto.OverrideRequest;
import com.mabini.parking.gate.dto.VisitorRequest;
import com.mabini.parking.log.dto.LogResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The gate simulator endpoints. Parking managers and the super admin only. */
@RestController
@RequestMapping("/api/manager/gate")
@PreAuthorize(Roles.PARKING_MANAGER_ONLY)
public class GateController {

    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    /** A plate arrives at the gate (direction IN) or leaves (OUT). Returns allowed or denied. */
    @PostMapping("/verify")
    public GateResult verify(@AuthenticationPrincipal AuthUser current, @Valid @RequestBody GateRequest request) {
        return gateService.verify(current.id(), request);
    }

    @PostMapping("/visitor-entry")
    public GateResult visitorEntry(@AuthenticationPrincipal AuthUser current,
                                   @Valid @RequestBody VisitorRequest request) {
        return gateService.visitorEntry(current.id(), request);
    }

    /** Let a vehicle in by hand. A reason is required and is saved in the log. */
    @PostMapping("/override")
    public GateResult override(@AuthenticationPrincipal AuthUser current,
                               @Valid @RequestBody OverrideRequest request) {
        return gateService.overrideEntry(current.id(), request);
    }

    /** Everyone inside the parking lot right now. */
    @GetMapping("/inside")
    public List<LogResponse> inside() {
        return gateService.inside();
    }

    /** Record the exit of a visit by its id (used for visitors, who have no account). */
    @PostMapping("/exit/{logId}")
    public LogResponse exit(@PathVariable Long logId) {
        return gateService.exitByLogId(logId);
    }
}
