package com.mabini.parking.reservation.dto;

import jakarta.validation.constraints.Size;

/** The reason is optional but shown to the user. */
public record RejectRequest(
        @Size(max = 255, message = "The reason can be at most 255 characters.")
        String note) {
}
