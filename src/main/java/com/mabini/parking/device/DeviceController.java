package com.mabini.parking.device;

import com.mabini.parking.common.Roles;
import com.mabini.parking.device.dto.DeviceGateRequest;
import com.mabini.parking.device.dto.HeartbeatRequest;
import com.mabini.parking.gate.GateService;
import com.mabini.parking.gate.dto.GateRequest;
import com.mabini.parking.gate.dto.GateResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Endpoints for the gate hardware (ESP32). Every call needs the header "X-Device-Key: <secret>"
 * (see DeviceKeyFilter). A device key opens ONLY these endpoints, nothing else in the system.
 *
 * The decision is made by exactly the same rules as the manual gate page (GateService.verify),
 * so a plate is treated the same whether the hardware read it or a person typed it.
 */
@RestController
@RequestMapping("/api/device")
@PreAuthorize(Roles.DEVICE_ONLY)
public class DeviceController {

    public record HeartbeatResponse(boolean ok, LocalDateTime serverTime) {
    }

    public record AllowedPlates(LocalDateTime generatedAt, List<String> plates) {
    }

    private final GateService gateService;
    private final DeviceRegistry registry;
    private final Clock clock;

    public DeviceController(GateService gateService, DeviceRegistry registry, Clock clock) {
        this.gateService = gateService;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * A plate was read. The answer says "allowed": true means open the barrier.
     * "message" is short text for the screen. "reason" says why it was refused.
     */
    @PostMapping("/gate")
    public GateResult gate(@Valid @RequestBody DeviceGateRequest request) {
        registry.touch(request.deviceId());
        // No staff member is involved, so "recorded by" stays empty: that is how hardware entries are told apart.
        return gateService.verify(null, new GateRequest(request.identifier(), request.direction()));
    }

    /**
     * The plates the gate would let in right now (approved vehicle, active owner, not suspended, not already inside,
     * valid reservation). A gate controller can keep this list and open the barrier by itself when a plate is on it.
     * Read-only: nothing is recorded. The list changes over time, so fetch it again every few seconds.
     */
    @GetMapping("/allowed-plates")
    public AllowedPlates allowedPlates() {
        return new AllowedPlates(LocalDateTime.now(clock), gateService.allowedPlatesNow());
    }

    /** "I am alive." Send this every 10 seconds or so. */
    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        registry.touch(request.deviceId());
        return new HeartbeatResponse(true, LocalDateTime.now(clock));
    }
}
