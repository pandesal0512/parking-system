package com.mabini.parking.log.dto;

import com.mabini.parking.common.enums.DenyReason;
import com.mabini.parking.log.DeniedAttempt;

import java.time.LocalDateTime;

public record DeniedResponse(
        Long id,
        String identifier,
        DenyReason reason,
        LocalDateTime attemptedAt) {

    public static DeniedResponse from(DeniedAttempt d) {
        return new DeniedResponse(d.getId(), d.getIdentifierRead(), d.getReason(), d.getAttemptedAt());
    }
}
