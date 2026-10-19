package com.airtribe.smart_parking_lot_system.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String reference) {
        super("No open ticket found for: " + reference);
    }
}
