package com.mabini.parking.capacity;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.user.User;
import org.springframework.stereotype.Component;

/**
 * Decides which area a member's vehicle uses.
 * Faculty and personnel use the FACULTY area when it has spaces for that vehicle type.
 * If the super admin sets that area's capacity to 0, those vehicles use the general lot instead.
 * Students always use the general lot.
 */
@Component
public class AreaResolver {

    private final ParkingCapacityRepository capacities;

    public AreaResolver(ParkingCapacityRepository capacities) {
        this.capacities = capacities;
    }

    public ParkingArea resolve(User owner, VehicleType type) {
        if (owner != null && ParkingArea.isFacultyType(owner.getUserType())) {
            boolean facultyHasSpaces = capacities.findByAreaAndVehicleType(ParkingArea.FACULTY, type)
                    .map(c -> c.getCapacity() > 0).orElse(false);
            if (facultyHasSpaces) {
                return ParkingArea.FACULTY;
            }
        }
        return ParkingArea.GENERAL;
    }
}
