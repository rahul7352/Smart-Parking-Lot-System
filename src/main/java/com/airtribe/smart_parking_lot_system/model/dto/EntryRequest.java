package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EntryRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9 -]{3,20}$", message = "must be 3-20 letters, digits, spaces or hyphens")
        String plateNumber,
        @NotNull VehicleType vehicleType) {
}
