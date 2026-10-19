package com.airtribe.smart_parking_lot_system.model.dto;

import java.time.Instant;
import java.util.UUID;

public record EntryResponse(UUID ticketCode, int floorNumber, int spotNumber, Instant entryTime, boolean existingTicket) {
}
