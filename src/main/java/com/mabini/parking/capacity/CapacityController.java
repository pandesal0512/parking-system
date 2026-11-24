package com.mabini.parking.capacity;

import com.mabini.parking.capacity.dto.CapacityRequest;
import com.mabini.parking.capacity.dto.CapacityResponse;
import com.mabini.parking.common.Roles;
import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CapacityController {

    private final CapacityService capacityService;

    public CapacityController(CapacityService capacityService) {
        this.capacityService = capacityService;
    }

    /** Any logged-in person may see the capacities. */
    @GetMapping("/capacity")
    public List<CapacityResponse> list() {
        return capacityService.list();
    }

    /** Example: PUT /api/admin/capacity/FACULTY/CAR  {"capacity": 12} */
    @PutMapping("/admin/capacity/{area}/{type}")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public CapacityResponse update(@PathVariable ParkingArea area, @PathVariable VehicleType type,
                                   @Valid @RequestBody CapacityRequest request) {
        return capacityService.update(area, type, request.capacity());
    }
}
