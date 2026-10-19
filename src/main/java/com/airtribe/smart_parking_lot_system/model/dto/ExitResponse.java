package com.airtribe.smart_parking_lot_system.model.dto;

import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExitResponse(UUID ticketCode, String plateNumber, Instant entryTime, Instant exitTime, long durationMinutes,
                           BigDecimal fee, TicketStatus status) {
}