package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import jakarta.validation.constraints.NotNull;

public record SpotStatusRequest(@NotNull SpotStatus status) {
}
