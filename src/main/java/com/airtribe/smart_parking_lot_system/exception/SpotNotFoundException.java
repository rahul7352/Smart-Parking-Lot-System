package com.airtribe.smart_parking_lot_system.exception;

public class SpotNotFoundException extends RuntimeException {
    public SpotNotFoundException(Long id) {
        super("Spot not found: " + id);
    }
}
