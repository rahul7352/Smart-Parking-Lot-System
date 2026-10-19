package com.airtribe.smart_parking_lot_system.exception;

public class NoSpotAvailableException extends RuntimeException {
    public NoSpotAvailableException(String message) {
        super(message);
    }
}
