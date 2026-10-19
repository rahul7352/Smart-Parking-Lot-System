package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.InvalidSpotStateException;
import com.airtribe.smart_parking_lot_system.exception.SpotNotFoundException;
import com.airtribe.smart_parking_lot_system.model.dto.AddSpotRequest;
import com.airtribe.smart_parking_lot_system.model.dto.SpotResponse;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SpotAdminService {

    private final ParkingSpotRepository parkingSpotRepository;

    /** Appends spots to a floor (creating the floor implicitly). Concurrent adds to one floor may 409. */
    @Transactional
    public List<SpotResponse> addSpots(AddSpotRequest request) {
        int start = parkingSpotRepository.maxSpotNumber(request.floor()) + 1;
        List<ParkingSpot> spotCreated = IntStream.range(0, request.count())
                .mapToObj(e -> new ParkingSpot(request.floor(), start + e, request.spotType()))
                .toList();

        return parkingSpotRepository.saveAll(spotCreated).stream().map(SpotAdminService::toResponse).toList();
    }

    @Transactional
    public SpotResponse setStatus(Long id, SpotStatus targetStatus) {
        ParkingSpot parkingSpot = parkingSpotRepository.findByIdForUpdate(id).orElseThrow(() -> new SpotNotFoundException(id));
        if(targetStatus == SpotStatus.OCCUPIED) {
            throw new InvalidSpotStateException("OCCUPIED is set by the system when a vehicle enters");
        }
        if(parkingSpot.getStatus() == SpotStatus.OCCUPIED) {
            throw new InvalidSpotStateException("Spot is occupied; wait until the vehicle leaves.");
        }

        parkingSpot.setStatus(targetStatus);
        return toResponse(parkingSpot);
    }

    private static SpotResponse toResponse(ParkingSpot spot) {
        return new SpotResponse(spot.getId(), spot.getFloorNumber(), spot.getSpotNumber(), spot.getSpotType(), spot.getStatus());
    }
}
