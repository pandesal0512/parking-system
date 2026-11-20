package com.mabini.parking.vehicle;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.common.Roles;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.vehicle.dto.VehicleRequest;
import com.mabini.parking.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /* ----- a user's own vehicle (the owner always comes from the login token) ----- */

    @GetMapping("/vehicles/me")
    @PreAuthorize(Roles.USER_ONLY)
    public VehicleResponse mine(@AuthenticationPrincipal AuthUser current) {
        return vehicleService.getMine(current.id());
    }

    @PostMapping("/vehicles/me")
    @PreAuthorize(Roles.USER_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse register(@AuthenticationPrincipal AuthUser current,
                                    @Valid @RequestBody VehicleRequest request) {
        return vehicleService.register(current.id(), request);
    }

    @PutMapping("/vehicles/me")
    @PreAuthorize(Roles.USER_ONLY)
    public VehicleResponse update(@AuthenticationPrincipal AuthUser current,
                                  @Valid @RequestBody VehicleRequest request) {
        return vehicleService.updateMine(current.id(), request);
    }

    /* ----- parking manager / super admin ----- */

    @GetMapping("/manager/vehicles")
    @PreAuthorize(Roles.STAFF)
    public List<VehicleResponse> list(@RequestParam(required = false) VehicleStatus status) {
        return vehicleService.list(status);
    }

    @PostMapping("/manager/vehicles/{id}/approve")
    @PreAuthorize(Roles.STAFF)
    public VehicleResponse approve(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return vehicleService.approve(id, current.id());
    }

    @PostMapping("/manager/vehicles/{id}/reject")
    @PreAuthorize(Roles.STAFF)
    public VehicleResponse reject(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return vehicleService.reject(id, current.id());
    }

    @PostMapping("/manager/vehicles/{id}/revoke")
    @PreAuthorize(Roles.STAFF)
    public VehicleResponse revoke(@PathVariable Long id, @AuthenticationPrincipal AuthUser current) {
        return vehicleService.revoke(id, current.id());
    }
}
