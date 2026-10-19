package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.SpotType;

import java.util.Map;

public record FloorAvailability(int floor, Map<SpotType, Long> free) {}