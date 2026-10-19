package com.airtribe.smart_parking_lot_system.strategy;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;

import java.util.Optional;

/**
 * Picks (and row-locks) a free spot for a vehicle type. Must run inside a transaction.
 * Extension point: a multi-spot bus strategy (N adjacent BUS spots on one floor) would implement
 * this interface and lock all N rows in a fixed order to avoid deadlocks.
 */
public interface AllocationStrategy {

    /** Empty when no UNLOCKED free spot exists right now. */
    Optional<ParkingSpot> findSpot(VehicleType vehicleType);

    /** True if any compatible spot is free, even if another transaction currently holds its lock. */
    boolean hasCapacity(VehicleType vehicleType);
}
