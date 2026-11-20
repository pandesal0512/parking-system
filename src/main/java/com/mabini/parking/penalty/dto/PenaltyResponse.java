package com.mabini.parking.penalty.dto;

import com.mabini.parking.common.enums.PenaltyType;
import com.mabini.parking.penalty.Penalty;

import java.time.LocalDateTime;

public record PenaltyResponse(
        Long id,
        String userName,
        String username,
        PenaltyType type,
        String description,
        LocalDateTime createdAt) {

    public static PenaltyResponse from(Penalty p) {
        return new PenaltyResponse(p.getId(), p.getUser().getFullName(), p.getUser().getUsername(),
                p.getType(), p.getDescription(), p.getCreatedAt());
    }
}
