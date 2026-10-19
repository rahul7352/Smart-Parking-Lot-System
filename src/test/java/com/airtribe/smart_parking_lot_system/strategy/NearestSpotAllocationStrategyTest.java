package com.airtribe.smart_parking_lot_system.strategy;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NearestSpotAllocationStrategyTest {

    @Mock
    ParkingSpotRepository spots;
    @InjectMocks
    NearestSpotAllocationStrategy strategy;

    private static ParkingSpot spot(SpotType type) {
        return new ParkingSpot(1, 1, type);
    }

    @Test
    void car_onlyLooksAtCarSpots_andLocksExactlyOneRow() {
        ParkingSpot car = spot(SpotType.CAR);
        when(spots.lockNextFree(eq(SpotType.CAR), eq(SpotStatus.FREE), any(Pageable.class)))
                .thenReturn(List.of(car));

        Optional<ParkingSpot> result = strategy.findSpot(VehicleType.CAR);

        assertThat(result).containsSame(car);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(spots).lockNextFree(eq(SpotType.CAR), eq(SpotStatus.FREE), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(1);
        verifyNoMoreInteractions(spots);
    }

    @Test
    void motorcycle_prefersItsOwnSpotType() {
        ParkingSpot bike = spot(SpotType.MOTORCYCLE);
        when(spots.lockNextFree(eq(SpotType.MOTORCYCLE), eq(SpotStatus.FREE), any(Pageable.class)))
                .thenReturn(List.of(bike));

        assertThat(strategy.findSpot(VehicleType.MOTORCYCLE)).containsSame(bike);
        verify(spots, never()).lockNextFree(eq(SpotType.CAR), any(), any());
    }

    @Test
    void motorcycle_fallsBackToACarSpot_whenNoMotorcycleSpotIsFree() {
        ParkingSpot car = spot(SpotType.CAR);
        when(spots.lockNextFree(eq(SpotType.MOTORCYCLE), eq(SpotStatus.FREE), any(Pageable.class)))
                .thenReturn(List.of());
        when(spots.lockNextFree(eq(SpotType.CAR), eq(SpotStatus.FREE), any(Pageable.class)))
                .thenReturn(List.of(car));

        assertThat(strategy.findSpot(VehicleType.MOTORCYCLE)).containsSame(car);
    }

    @Test
    void bus_neverFallsBackToSmallerSpots() {
        when(spots.lockNextFree(eq(SpotType.BUS), eq(SpotStatus.FREE), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(strategy.findSpot(VehicleType.BUS)).isEmpty();
        verify(spots, never()).lockNextFree(eq(SpotType.CAR), any(), any());
        verify(spots, never()).lockNextFree(eq(SpotType.MOTORCYCLE), any(), any());
    }

    @Test
    void hasCapacity_checksAllCompatibleTypes_forFreeSpots() {
        when(spots.existsBySpotTypeInAndStatus(List.of(SpotType.MOTORCYCLE, SpotType.CAR), SpotStatus.FREE))
                .thenReturn(true);
        when(spots.existsBySpotTypeInAndStatus(List.of(SpotType.BUS), SpotStatus.FREE)).thenReturn(false);

        assertThat(strategy.hasCapacity(VehicleType.MOTORCYCLE)).isTrue();
        assertThat(strategy.hasCapacity(VehicleType.BUS)).isFalse();
    }
}
