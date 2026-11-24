package com.mabini.parking.capacity;

import com.mabini.parking.capacity.dto.CapacityResponse;
import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CapacityService {

    private final ParkingCapacityRepository capacities;

    public CapacityService(ParkingCapacityRepository capacities) {
        this.capacities = capacities;
    }

    @Transactional(readOnly = true)
    public List<CapacityResponse> list() {
        return capacities.findAll().stream()
                .sorted(Comparator.comparing(ParkingCapacity::getArea).thenComparing(ParkingCapacity::getVehicleType))
                .map(c -> new CapacityResponse(c.getArea(), c.getVehicleType(), c.getCapacity()))
                .toList();
    }

    /**
     * Changing capacity does not touch existing reservations. If it is lowered below what is
     * already booked, those bookings stay valid and only NEW bookings are affected.
     */
    @Transactional
    public CapacityResponse update(ParkingArea area, VehicleType type, int capacity) {
        ParkingCapacity row = capacities.findByAreaAndVehicleType(area, type)
                .orElseThrow(() -> ApiException.notFound("CAPACITY_NOT_FOUND", "No capacity is set for that area and vehicle type."));
        row.setCapacity(capacity);
        return new CapacityResponse(row.getArea(), row.getVehicleType(), row.getCapacity());
    }
}
