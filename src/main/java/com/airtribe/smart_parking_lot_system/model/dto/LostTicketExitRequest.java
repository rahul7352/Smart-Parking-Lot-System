package com.airtribe.smart_parking_lot_system.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LostTicketExitRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,20}$", message = "must be 3-20 letters, digits, spaces or hyphens")
        String plateNumber) {
}
