package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.InvalidSpotStateException;
import com.airtribe.smart_parking_lot_system.exception.SpotNotFoundException;
import com.airtribe.smart_parking_lot_system.model.dto.AddSpotRequest;
import com.airtribe.smart_parking_lot_system.model.dto.SpotResponse;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotAdminServiceTest {

    @Mock
    ParkingSpotRepository spots;
    @InjectMocks
    SpotAdminService service;

    private static ParkingSpot spot(SpotStatus status) {
        ParkingSpot s = new ParkingSpot(1, 3, SpotType.CAR);
        s.setId(7L);
        s.setStatus(status);
        return s;
    }

    @Test
    void addSpots_continuesNumberingAfterTheHighestSpotOnTheFloor() {
        when(spots.maxSpotNumber(3)).thenReturn(4);
        when(spots.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SpotResponse> created = service.addSpots(new AddSpotRequest(3, SpotType.BUS, 3));

        assertThat(created).extracting(SpotResponse::spotNumber).containsExactly(5, 6, 7);
        assertThat(created).allMatch(s -> s.floor() == 3 && s.spotType() == SpotType.BUS
                && s.status() == SpotStatus.FREE);
    }

    @Test
    void addSpots_onAnEmptyFloor_startsAtOne() {
        when(spots.maxSpotNumber(9)).thenReturn(0);
        when(spots.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.addSpots(new AddSpotRequest(9, SpotType.CAR, 2)))
                .extracting(SpotResponse::spotNumber).containsExactly(1, 2);
    }

    @Test
    void setStatus_freeSpot_canBeTakenOutOfService() {
        ParkingSpot s = spot(SpotStatus.FREE);
        when(spots.findByIdForUpdate(7L)).thenReturn(Optional.of(s));

        SpotResponse response = service.setStatus(7L, SpotStatus.OUT_OF_SERVICE);

        assertThat(s.getStatus()).isEqualTo(SpotStatus.OUT_OF_SERVICE);
        assertThat(response.status()).isEqualTo(SpotStatus.OUT_OF_SERVICE);
    }

    @Test
    void setStatus_outOfServiceSpot_canBeFreedAgain() {
        ParkingSpot s = spot(SpotStatus.OUT_OF_SERVICE);
        when(spots.findByIdForUpdate(7L)).thenReturn(Optional.of(s));

        service.setStatus(7L, SpotStatus.FREE);

        assertThat(s.getStatus()).isEqualTo(SpotStatus.FREE);
    }

    @Test
    void setStatus_unknownSpot_throwsNotFound() {
        when(spots.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStatus(99L, SpotStatus.FREE))
                .isInstanceOf(SpotNotFoundException.class);
    }

    @Test
    void setStatus_cannotForceOccupied() {
        ParkingSpot s = spot(SpotStatus.FREE);
        when(spots.findByIdForUpdate(7L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.setStatus(7L, SpotStatus.OCCUPIED))
                .isInstanceOf(InvalidSpotStateException.class);
        assertThat(s.getStatus()).isEqualTo(SpotStatus.FREE);
    }

    @Test
    void setStatus_cannotChangeAnOccupiedSpot() {
        ParkingSpot s = spot(SpotStatus.OCCUPIED);
        when(spots.findByIdForUpdate(7L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.setStatus(7L, SpotStatus.OUT_OF_SERVICE))
                .isInstanceOf(InvalidSpotStateException.class);
        assertThat(s.getStatus()).isEqualTo(SpotStatus.OCCUPIED);
    }
}
