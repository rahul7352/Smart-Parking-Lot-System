package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.model.dto.AvailabilityResponse;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    ParkingSpotRepository spots;
    @InjectMocks
    AvailabilityService service;

    @Test
    void availability_aggregatesRowsPerFloor_andTotals() {
        when(spots.freeStatusCountsByFloorAndType(SpotStatus.FREE)).thenReturn(List.of(
                new Object[]{2, SpotType.CAR, 3L},
                new Object[]{1, SpotType.CAR, 2L},
                new Object[]{1, SpotType.BUS, 0L}));

        AvailabilityResponse response = service.availability();

        assertThat(response.totalFree())
                .containsEntry(SpotType.CAR, 5L)
                .containsEntry(SpotType.BUS, 0L)
                .containsEntry(SpotType.MOTORCYCLE, 0L);
        assertThat(response.floors()).extracting(f -> f.floor()).containsExactly(1, 2);   // sorted by floor
        assertThat(response.floors().get(0).free())
                .containsEntry(SpotType.CAR, 2L)
                .containsEntry(SpotType.BUS, 0L)
                .containsEntry(SpotType.MOTORCYCLE, 0L);
        assertThat(response.floors().get(1).free()).containsEntry(SpotType.CAR, 3L);
    }

    @Test
    void availability_emptyLot_hasZeroTotals_andNoFloors() {
        when(spots.freeStatusCountsByFloorAndType(SpotStatus.FREE)).thenReturn(List.of());

        AvailabilityResponse response = service.availability();

        assertThat(response.floors()).isEmpty();
        assertThat(response.totalFree().values()).containsOnly(0L);
        assertThat(response.totalFree()).hasSize(SpotType.values().length);
    }

    @Test
    void freeSpots_returnsTheTotals() {
        when(spots.freeStatusCountsByFloorAndType(SpotStatus.FREE))
                .thenReturn(List.<Object[]>of(new Object[]{1, SpotType.BUS, 4L}));

        assertThat(service.freeSpots()).containsEntry(SpotType.BUS, 4L);
    }
}
