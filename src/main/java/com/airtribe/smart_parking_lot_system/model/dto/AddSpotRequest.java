package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddSpotRequest(@Min(1) int floor, @NotNull SpotType spotType,
                             @Min(1) @Max(500) int count) {
}
