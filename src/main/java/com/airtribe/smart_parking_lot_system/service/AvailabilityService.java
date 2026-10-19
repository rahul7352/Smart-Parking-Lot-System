package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.model.dto.AvailabilityResponse;
import com.airtribe.smart_parking_lot_system.model.dto.FloorAvailability;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ParkingSpotRepository parkingSpotRepository;

    /** Free spots per floor and spot type. RESERVED / OUT_OF_SERVICE / OCCUPIED are not counted. */
    @Transactional(readOnly = true)
    public AvailabilityResponse availability() {
        Map<Integer, Map<SpotType, Long>> byFloor = new TreeMap<>();
        Map<SpotType, Long> totalFree = emptyCounts();

        for(Object[] row : parkingSpotRepository.freeStatusCountsByFloorAndType(SpotStatus.FREE)) {
            int floor = ((Number) row[0]).intValue();
            SpotType spotType = (SpotType) row[1];
            long freeCount = ((Number) row[2]).longValue();
            byFloor.computeIfAbsent(floor, f -> emptyCounts()).put(spotType, freeCount);
            totalFree.merge(spotType, freeCount, Long::sum);
        }

        List<FloorAvailability> availableFloors = byFloor.entrySet().stream()
                .map(e -> new FloorAvailability(e.getKey(), e.getValue()))
                .toList();

        return new AvailabilityResponse(totalFree, availableFloors);
    }

    public Map<SpotType, Long> freeSpots() {
        return availability().totalFree();
    }

    private static Map<SpotType, Long> emptyCounts() {
        Map<SpotType, Long> map = new EnumMap<>(SpotType.class);
        for(SpotType type : SpotType.values()) {
            map.put(type, 0L);
        }
        return map;
    }
}
