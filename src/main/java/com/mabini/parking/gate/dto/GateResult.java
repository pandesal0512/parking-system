package com.mabini.parking.gate.dto;

import com.mabini.parking.common.enums.Direction;
import com.mabini.parking.common.enums.VehicleType;

/**
 * The gate's answer. A refusal is a normal answer (allowed = false), not an error,
 * so the response status is 200 either way.
 */
public record GateResult(
        boolean allowed,
        String reason,
        String message,
        Direction direction,
        String identifier,
        String ownerName,
        VehicleType vehicleType) {
}
