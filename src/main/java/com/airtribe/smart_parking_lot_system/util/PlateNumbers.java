package com.airtribe.smart_parking_lot_system.util;

import java.util.Locale;

public final class PlateNumbers {

    private PlateNumbers() {}

    /** "  ts09-ab 1234 " -> "TS09AB1234". Single source of truth for plate identity. */
    public static String normalize(String plateNumber) {
        if(plateNumber == null) {
            throw new IllegalArgumentException("Plate number cannot be null");
        }
        String normalize = plateNumber.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]", "");
        if(normalize.length() < 3 || !normalize.matches("[A-Z)-9]+")) {
            throw new IllegalArgumentException("Invalid number plate");
        }
        return normalize;
    }
}
