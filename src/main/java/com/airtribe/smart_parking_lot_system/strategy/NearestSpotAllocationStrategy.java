package com.airtribe.smart_parking_lot_system.strategy;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lowest floor / lowest spot number first; tries preferred spot types in order. */
@Component
public class NearestSpotAllocationStrategy implements AllocationStrategy {

    // Preference order: exact fit first, then a larger spot as fallback.
    private final Map<VehicleType, List<SpotType>> COMPATIBLE = Map.of(
            VehicleType.MOTORCYCLE, List.of(SpotType.MOTORCYCLE, SpotType.CAR),
            VehicleType.CAR, List.of(SpotType.CAR),
            VehicleType.BUS, List.of(SpotType.BUS)
    );

    private final ParkingSpotRepository parkingSpotRepository;

    public NearestSpotAllocationStrategy(ParkingSpotRepository parkingSpotRepository) {
        this.parkingSpotRepository = parkingSpotRepository;
    }
    @Override
    public Optional<ParkingSpot> findSpot(VehicleType vehicleType) {
        for(SpotType type : COMPATIBLE.get(vehicleType)) {
            List<ParkingSpot> spots = parkingSpotRepository.lockNextFree(type, SpotStatus.FREE, PageRequest.of(0, 1));
            if(!spots.isEmpty()) {
                return Optional.of(spots.get(0));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasCapacity(VehicleType vehicleType) {
        return parkingSpotRepository.existsBySpotTypeInAndStatus(COMPATIBLE.get(vehicleType), SpotStatus.FREE);
    }
}
