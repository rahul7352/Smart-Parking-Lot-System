package com.airtribe.smart_parking_lot_system.model.enums;

/** ACTIVE (parked) -> PAYMENT_PENDING (checked out, fee due) -> CLOSED. Zero fee skips PAYMENT_PENDING. */
public enum TicketStatus { ACTIVE, PAYMENT_PENDING, CLOSED }
