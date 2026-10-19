package com.airtribe.smart_parking_lot_system.exception;

public class PaymentPendingException extends RuntimeException {
    public PaymentPendingException(String plateNumber) {
        super("Outstanding payment must be settled before re-entry: " + plateNumber);
    }
}
