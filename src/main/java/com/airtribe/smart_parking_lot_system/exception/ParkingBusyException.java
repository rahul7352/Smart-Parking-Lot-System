package com.airtribe.smart_parking_lot_system.exception;

public class ParkingBusyException extends RuntimeException {
    public ParkingBusyException(String message) {
        super(message);
    }
}
