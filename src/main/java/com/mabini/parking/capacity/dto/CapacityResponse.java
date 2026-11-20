package com.mabini.parking.capacity.dto;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;

public record CapacityResponse(ParkingArea area, VehicleType vehicleType, int capacity) {
}
