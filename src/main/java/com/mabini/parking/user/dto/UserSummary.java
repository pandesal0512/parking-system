package com.mabini.parking.user.dto;

import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;

/** One row of the manager's user list: the account plus its single vehicle, if any. */
public record UserSummary(
        UserResponse user,
        String plate,
        VehicleType vehicleType,
        VehicleStatus vehicleStatus) {
}
