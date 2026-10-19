package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingSpotRepositoryTest extends AbstractRepositoryTest {

    @Autowired ParkingSpotRepository spots;

    private ParkingSpot save(int floor, int number, SpotType type, SpotStatus status) {
        ParkingSpot s = new ParkingSpot(floor, number, type);
        s.setStatus(status);
        return em.persistAndFlush(s);
    }

    @Test
    void lockNextFree_returnsLowestFloorThenLowestSpotNumber() {
        save(2, 1, SpotType.CAR, SpotStatus.FREE);
        save(1, 5, SpotType.CAR, SpotStatus.FREE);
        save(1, 2, SpotType.CAR, SpotStatus.FREE);

        List<ParkingSpot> result = spots.lockNextFree(SpotType.CAR, SpotStatus.FREE, PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFloorNumber()).isEqualTo(1);
        assertThat(result.get(0).getSpotNumber()).isEqualTo(2);
    }

    @Test
    void lockNextFree_ignoresOtherStatusesAndOtherTypes() {
        save(1, 1, SpotType.CAR, SpotStatus.OCCUPIED);
        save(1, 2, SpotType.CAR, SpotStatus.OUT_OF_SERVICE);
        save(1, 3, SpotType.CAR, SpotStatus.RESERVED);
        save(1, 4, SpotType.BUS, SpotStatus.FREE);

        assertThat(spots.lockNextFree(SpotType.CAR, SpotStatus.FREE, PageRequest.of(0, 1))).isEmpty();
    }

    @Test
    void lockNextFree_respectsPageSize() {
        save(1, 1, SpotType.CAR, SpotStatus.FREE);
        save(1, 2, SpotType.CAR, SpotStatus.FREE);
        save(1, 3, SpotType.CAR, SpotStatus.FREE);

        assertThat(spots.lockNextFree(SpotType.CAR, SpotStatus.FREE, PageRequest.of(0, 2))).hasSize(2);
    }

    @Test
    void existsBySpotTypeInAndStatus_matchesAnyOfTheGivenTypes() {
        save(1, 1, SpotType.CAR, SpotStatus.FREE);
        save(1, 2, SpotType.BUS, SpotStatus.OCCUPIED);

        assertThat(spots.existsBySpotTypeInAndStatus(List.of(SpotType.MOTORCYCLE, SpotType.CAR), SpotStatus.FREE))
                .isTrue();
        assertThat(spots.existsBySpotTypeInAndStatus(List.of(SpotType.BUS), SpotStatus.FREE)).isFalse();
        assertThat(spots.existsBySpotTypeInAndStatus(List.of(SpotType.MOTORCYCLE), SpotStatus.FREE)).isFalse();
    }

    @Test
    void freeCountsByFloorAndType_countsOnlyFreeButKeepsFullGroups() {
        save(1, 1, SpotType.CAR, SpotStatus.FREE);
        save(1, 2, SpotType.CAR, SpotStatus.OCCUPIED);
        save(2, 1, SpotType.BUS, SpotStatus.OCCUPIED);
        save(2, 2, SpotType.BUS, SpotStatus.OUT_OF_SERVICE);

        Map<String, Long> byKey = spots.freeStatusCountsByFloorAndType(SpotStatus.FREE).stream()
                .collect(Collectors.toMap(r -> r[0] + "-" + r[1], r -> ((Number) r[2]).longValue()));

        assertThat(byKey).hasSize(2).containsEntry("1-CAR", 1L).containsEntry("2-BUS", 0L);
    }

    @Test
    void maxSpotNumber_isZeroForEmptyFloor_andHighestOtherwise() {
        save(1, 1, SpotType.CAR, SpotStatus.FREE);
        save(1, 7, SpotType.CAR, SpotStatus.FREE);

        assertThat(spots.maxSpotNumber(1)).isEqualTo(7);
        assertThat(spots.maxSpotNumber(9)).isZero();
    }

    @Test
    void findByIdForUpdate_returnsSpotOrEmpty() {
        ParkingSpot s = save(1, 1, SpotType.CAR, SpotStatus.FREE);

        assertThat(spots.findByIdForUpdate(s.getId())).isPresent();
        assertThat(spots.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void floorAndSpotNumber_mustBeUnique() {
        save(1, 1, SpotType.CAR, SpotStatus.FREE);

        assertThatThrownBy(() -> spots.saveAndFlush(new ParkingSpot(1, 1, SpotType.BUS)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
