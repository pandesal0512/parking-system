package com.mabini.parking.capacity.dto;

import com.mabini.parking.common.enums.VehicleType;

public record CapacityResponse(VehicleType vehicleType, int capacity) {
}
