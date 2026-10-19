package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.SpotType;

import java.util.List;
import java.util.Map;

public record AvailabilityResponse(Map<SpotType, Long> totalFree, List<FloorAvailability> floors) {}