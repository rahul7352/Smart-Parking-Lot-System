package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.NoSpotAvailableException;
import com.airtribe.smart_parking_lot_system.exception.ParkingBusyException;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.strategy.AllocationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotAllocationServiceTest {

    @Mock
    AllocationStrategy strategy;
    SpotAllocationService service;
    ParkingSpot spot;

    @BeforeEach
    void setUp() {
        service = new SpotAllocationService(strategy);
        spot = new ParkingSpot(1, 1, SpotType.CAR);
        ReflectionTestUtils.setField(service, "maxRetry", 3);
        ReflectionTestUtils.setField(service, "backOffInMS", 20);
    }

    @Test
    void allocate_marksTheFoundSpotOccupied() {
        when(strategy.findSpot(VehicleType.CAR)).thenReturn(Optional.of(spot));

        ParkingSpot result = service.allocate(VehicleType.CAR);

        assertThat(result).isSameAs(spot);
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.OCCUPIED);
        verify(strategy, never()).hasCapacity(any());
    }

    @Test
    void allocate_lotReallyFull_failsImmediately_withoutRetrying() {
        when(strategy.findSpot(VehicleType.CAR)).thenReturn(Optional.empty());
        when(strategy.hasCapacity(VehicleType.CAR)).thenReturn(false);

        assertThatThrownBy(() -> service.allocate(VehicleType.CAR))
                .isInstanceOf(NoSpotAvailableException.class);

        verify(strategy, times(1)).findSpot(VehicleType.CAR);
    }

    @Test
    void allocate_freeSpotsMerelyLocked_retriesAndSucceeds() {
        when(strategy.findSpot(VehicleType.CAR)).thenReturn(Optional.empty(), Optional.of(spot));
        when(strategy.hasCapacity(VehicleType.CAR)).thenReturn(true);

        ParkingSpot result = service.allocate(VehicleType.CAR);

        assertThat(result).isSameAs(spot);
        verify(strategy, times(2)).findSpot(VehicleType.CAR);
    }

    @Test
    void allocate_spotsStayLocked_givesUpAfterFiveAttempts_asBusy() {
        when(strategy.findSpot(VehicleType.CAR)).thenReturn(Optional.empty());
        when(strategy.hasCapacity(VehicleType.CAR)).thenReturn(true);

        assertThatThrownBy(() -> service.allocate(VehicleType.CAR))
                .isInstanceOf(ParkingBusyException.class);

        verify(strategy, times(3)).findSpot(VehicleType.CAR);
    }

    @Test
    void allocate_interruptedWhileBackingOff_failsAsBusy_andKeepsInterruptFlag() {
        when(strategy.findSpot(VehicleType.CAR)).thenReturn(Optional.empty());
        when(strategy.hasCapacity(VehicleType.CAR)).thenReturn(true);
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> service.allocate(VehicleType.CAR))
                    .isInstanceOf(ParkingBusyException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();   // clear the flag so other tests are unaffected
        }
    }

    @Test
    void release_marksTheSpotFree() {
        spot.setStatus(SpotStatus.OCCUPIED);

        service.release(spot);

        assertThat(spot.getStatus()).isEqualTo(SpotStatus.FREE);
    }

    private static VehicleType any() {
        return org.mockito.ArgumentMatchers.any(VehicleType.class);
    }
}
