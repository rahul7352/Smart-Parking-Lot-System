package com.airtribe.smart_parking_lot_system.model.enums;

/** Only FREE spots are ever allocated. OCCUPIED is system-managed; the rest are set by staff. */
public enum SpotStatus {
    FREE, OCCUPIED, RESERVED, OUT_OF_SERVICE;
}
