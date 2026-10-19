package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.NoSpotAvailableException;
import com.airtribe.smart_parking_lot_system.exception.ParkingBusyException;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.strategy.AllocationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpotAllocationService {

    @Value("${retry.attempts}")
    private int maxRetry;

    @Value("${retry.backoff}")
    private int backOffInMS;

    private final AllocationStrategy strategy;

    /**
     * Joins the caller's transaction so the row lock is held until the ticket is saved.
     * SKIP LOCKED can momentarily hide free spots that other in-flight entries have locked, so
     * "nothing unlocked" is only reported as "lot full" when no free spot exists at all;
     * otherwise we back off briefly and retry (a locker may roll back and release its spot).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ParkingSpot allocate(VehicleType vehicleType) {
        for(int attempt = 1; attempt <= maxRetry; attempt++) {
            Optional<ParkingSpot> spotOptional = strategy.findSpot(vehicleType);
            if(spotOptional.isPresent()) {
                ParkingSpot spot = spotOptional.get();
                spot.setStatus(SpotStatus.OCCUPIED);
                return spot;
            }
            if(!strategy.hasCapacity(vehicleType)) {
                throw new NoSpotAvailableException("No free spot for " + vehicleType);
            }
            pause(backOffInMS * attempt);
        }
        throw new ParkingBusyException("Parking is busy, please retry shortly");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(ParkingSpot spot) {
        spot.setStatus(SpotStatus.FREE);
    }

    private void pause(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ParkingBusyException("Interrupted while waiting for spot");
        }
    }

}
