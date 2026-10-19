package com.airtribe.smart_parking_lot_system.integration;

import com.airtribe.smart_parking_lot_system.exception.*;
import com.airtribe.smart_parking_lot_system.model.dto.*;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.entity.Ticket;
import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.service.AvailabilityService;
import com.airtribe.smart_parking_lot_system.service.ParkingService;
import com.airtribe.smart_parking_lot_system.service.SpotAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Single-threaded edge cases against a real database. */
class ParkingFlowTest extends AbstractPostgresTest {

    private static final String PLATE = "TS09AB1234";

    @Autowired
    ParkingService parkingService;
    @Autowired
    AvailabilityService availabilityService;
    @Autowired
    SpotAdminService spotAdminService;
    @Autowired PlatformTransactionManager txManager;

    private static EntryRequest car(String plate) {
        return new EntryRequest(plate, VehicleType.CAR);
    }

    private Long spotId(int spotNumber) {
        return spotRepository.findAll().stream()
                .filter(s -> s.getSpotNumber() == spotNumber).findFirst().orElseThrow().getId();
    }

    // ---- gap 1: vehicle type comes from the current request ----
    @Test
    void returningPlateWithDifferentType_isAllocatedByRequestedType() {
        seedSpots(1, SpotType.CAR, 2);
        seedSpots(2, SpotType.BUS, 1);
        EntryResponse first = parkingService.enter(car(PLATE));
        parkingService.exit(first.ticketCode());

        EntryResponse second = parkingService.enter(new EntryRequest(PLATE, VehicleType.BUS));

        assertThat(second.floorNumber()).isEqualTo(2);
        assertThat(vehicleRepository.findByPlateNumber(PLATE).orElseThrow().getVehicleType())
                .isEqualTo(VehicleType.BUS);
    }

    // ---- gap 2: plate normalisation ----
    @Test
    void plateVariants_resolveToOneVehicleAndOneTicket() {
        seedSpots(1, SpotType.CAR, 3);
        EntryResponse a = parkingService.enter(car(" ts09-ab 1234 "));
        EntryResponse b = parkingService.enter(car("TS09AB1234"));

        assertThat(b.existingTicket()).isTrue();
        assertThat(b.ticketCode()).isEqualTo(a.ticketCode());
        assertThat(vehicleRepository.count()).isEqualTo(1);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(2L);
    }

    // ---- gap 6: retried entry returns the existing ticket ----
    @Test
    void duplicateEntry_returnsExistingTicket_withoutConsumingAnotherSpot() {
        seedSpots(1, SpotType.CAR, 2);
        EntryResponse first = parkingService.enter(car(PLATE));
        EntryResponse retry = parkingService.enter(car(PLATE));

        assertThat(first.existingTicket()).isFalse();
        assertThat(retry.existingTicket()).isTrue();
        assertThat(retry.ticketCode()).isEqualTo(first.ticketCode());
        assertThat(retry.spotNumber()).isEqualTo(first.spotNumber());
        assertThat(ticketRepository.count()).isEqualTo(1);
    }

    // ---- no spot free ----
    @Test
    void noSpotForVehicleType_throws_andLeavesNoTraceBehind() {
        seedSpots(1, SpotType.CAR, 1);

        assertThatThrownBy(() -> parkingService.enter(new EntryRequest(PLATE, VehicleType.BUS)))
                .isInstanceOf(NoSpotAvailableException.class);

        assertThat(ticketRepository.count()).isZero();
        assertThat(vehicleRepository.count()).isZero();   // vehicle insert rolled back with the transaction
    }

    @Test
    void lotFull_secondCarIsRejected_andGetsInOnceFirstLeaves() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse first = parkingService.enter(car(PLATE));

        assertThatThrownBy(() -> parkingService.enter(car("KA01XY9999")))
                .isInstanceOf(NoSpotAvailableException.class);

        parkingService.exit(first.ticketCode());   // inside the grace period: closes immediately
        assertThat(parkingService.enter(car("KA01XY9999")).existingTicket()).isFalse();
    }

    @Test
    void motorcycle_fallsBackToCarSpot_whenNoMotorcycleSpotIsFree() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse r = parkingService.enter(new EntryRequest("MC01AA0001", VehicleType.MOTORCYCLE));
        assertThat(r.floorNumber()).isEqualTo(1);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isZero();
    }

    // ---- gap 11: spot states ----
    @Test
    void outOfServiceSpot_isNeverAllocated_andNotCounted() {
        seedSpots(1, SpotType.CAR, 2);
        Long first = spotId(1);
        spotAdminService.setStatus(first, SpotStatus.OUT_OF_SERVICE);

        EntryResponse r = parkingService.enter(car(PLATE));
        assertThat(r.spotNumber()).isEqualTo(2);
        assertThatThrownBy(() -> parkingService.enter(car("KA01XY9999")))
                .isInstanceOf(NoSpotAvailableException.class);

        spotAdminService.setStatus(first, SpotStatus.FREE);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(1L);
    }

    @Test
    void adminCannotTouchOccupiedSpots_orForceOccupied_orUnknownSpots() {
        seedSpots(1, SpotType.CAR, 2);
        parkingService.enter(car(PLATE));   // occupies spot 1

        assertThatThrownBy(() -> spotAdminService.setStatus(spotId(1), SpotStatus.OUT_OF_SERVICE))
                .isInstanceOf(InvalidSpotStateException.class);
        assertThatThrownBy(() -> spotAdminService.setStatus(spotId(2), SpotStatus.OCCUPIED))
                .isInstanceOf(InvalidSpotStateException.class);
        assertThatThrownBy(() -> spotAdminService.setStatus(999_999L, SpotStatus.FREE))
                .isInstanceOf(SpotNotFoundException.class);
    }

    @Test
    void adminAddSpots_numbersContinueOnTheFloor() {
        List<SpotResponse> first = spotAdminService.addSpots(new AddSpotRequest(3, SpotType.BUS, 3));
        List<SpotResponse> more = spotAdminService.addSpots(new AddSpotRequest(3, SpotType.CAR, 2));

        assertThat(first).extracting(SpotResponse::spotNumber).containsExactly(1, 2, 3);
        assertThat(more).extracting(SpotResponse::spotNumber).containsExactly(4, 5);
        assertThat(availabilityService.freeSpots().get(SpotType.BUS)).isEqualTo(3L);
    }

    // ---- gap 4: availability per floor and type ----
    @Test
    void availability_isReportedPerFloorAndType() {
        seedSpots(1, SpotType.CAR, 2);
        seedSpots(2, SpotType.CAR, 1);
        seedSpots(2, SpotType.BUS, 1);
        parkingService.enter(car(PLATE));   // takes floor 1

        AvailabilityResponse a = availabilityService.availability();

        assertThat(a.totalFree()).containsEntry(SpotType.CAR, 2L)
                .containsEntry(SpotType.BUS, 1L).containsEntry(SpotType.MOTORCYCLE, 0L);
        assertThat(a.floors()).hasSize(2);
        assertThat(a.floors().get(0).floor()).isEqualTo(1);
        assertThat(a.floors().get(0).free()).containsEntry(SpotType.CAR, 1L);
        assertThat(a.floors().get(1).free()).containsEntry(SpotType.CAR, 1L).containsEntry(SpotType.BUS, 1L);
    }

    // ---- gaps 3 + 12: checkout, frozen fee, payment ----
    @Test
    void exitWithinGracePeriod_closesImmediately_withZeroFee() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse entry = parkingService.enter(car(PLATE));
        clock.advance(Duration.ofMinutes(10));

        ExitResponse exit = parkingService.exit(entry.ticketCode());

        assertThat(exit.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(exit.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(1L);
    }

    @Test
    void paidStay_goesThroughPaymentPending_beforeTheSpotIsFreed() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse entry = parkingService.enter(car(PLATE));
        clock.advance(Duration.ofMinutes(61));   // 61 min -> 2 started hours x 30.00

        ExitResponse exit = parkingService.exit(entry.ticketCode());
        assertThat(exit.status()).isEqualTo(TicketStatus.PAYMENT_PENDING);
        assertThat(exit.fee()).isEqualByComparingTo("60.00");
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isZero();   // vehicle still inside

        clock.advance(Duration.ofHours(3));       // fee is frozen at checkout, later calls don't change it
        ExitResponse again = parkingService.exit(entry.ticketCode());
        assertThat(again.fee()).isEqualByComparingTo("60.00");
        assertThat(again.exitTime()).isEqualTo(exit.exitTime());

        assertThatThrownBy(() -> parkingService.enter(car(PLATE))).isInstanceOf(PaymentPendingException.class);

        ExitResponse paid = parkingService.pay(entry.ticketCode(), "UPI-123");
        assertThat(paid.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(availabilityService.freeSpots().get(SpotType.CAR)).isEqualTo(1L);
        assertThat(parkingService.pay(entry.ticketCode(), "UPI-123").status()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void payBeforeCheckout_isRejected() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse entry = parkingService.enter(car(PLATE));
        assertThatThrownBy(() -> parkingService.pay(entry.ticketCode(), "REF"))
                .isInstanceOf(InvalidTicketStateException.class);
    }

    // ---- gap 10: lost ticket / unknown ticket ----
    @Test
    void lostTicket_canExitByPlate_oncePerStay() {
        seedSpots(1, SpotType.CAR, 1);
        parkingService.enter(car(PLATE));
        clock.advance(Duration.ofMinutes(5));

        ExitResponse exit = parkingService.exitByPlateNumber("  ts09ab1234 ");

        assertThat(exit.status()).isEqualTo(TicketStatus.CLOSED);
        assertThatThrownBy(() -> parkingService.exitByPlateNumber(PLATE)).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void unknownTicket_isNotFound() {
        assertThatThrownBy(() -> parkingService.exit(UUID.randomUUID()))
                .isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> parkingService.pay(UUID.randomUUID(), "REF"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    // ---- bidirectional Vehicle <-> Ticket mapping ----
    @Test
    void vehicleTickets_bothSidesStayInSync_acrossVisits() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse first = parkingService.enter(car(PLATE));
        parkingService.exit(first.ticketCode());
        EntryResponse second = parkingService.enter(car(PLATE));

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Vehicle vehicle = vehicleRepository.findByPlateNumber(PLATE).orElseThrow();
            assertThat(vehicle.getTickets()).extracting(Ticket::getTicketCode)
                    .containsExactlyInAnyOrder(first.ticketCode(), second.ticketCode());
            assertThat(vehicle.getTickets()).allMatch(t -> t.getVehicle() == vehicle);
        });
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    void spotTickets_bothSidesStayInSync_whenASpotIsReused() {
        seedSpots(1, SpotType.CAR, 1);
        EntryResponse first = parkingService.enter(car(PLATE));
        parkingService.exit(first.ticketCode());
        EntryResponse second = parkingService.enter(car("KA01XY9999"));   // same single spot, new visit

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            ParkingSpot spot = spotRepository.findAll().get(0);
            assertThat(spot.getTickets()).extracting(Ticket::getTicketCode)
                    .containsExactlyInAnyOrder(first.ticketCode(), second.ticketCode());
            assertThat(spot.getTickets()).allMatch(t -> t.getSpot() == spot);
        });
        assertThat(ticketRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidPlate_isRejected() {
        seedSpots(1, SpotType.CAR, 1);
        assertThatThrownBy(() -> parkingService.enter(car("---"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ticketRepository.count()).isZero();
    }
}
