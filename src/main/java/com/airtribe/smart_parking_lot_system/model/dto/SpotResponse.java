package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;

public record SpotResponse(Long id, int floor, int spotNumber, SpotType spotType, SpotStatus status) {
}
