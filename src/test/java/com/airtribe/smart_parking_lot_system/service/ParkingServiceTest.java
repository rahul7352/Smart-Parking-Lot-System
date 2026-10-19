package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.InvalidTicketStateException;
import com.airtribe.smart_parking_lot_system.exception.NoSpotAvailableException;
import com.airtribe.smart_parking_lot_system.exception.PaymentPendingException;
import com.airtribe.smart_parking_lot_system.exception.TicketNotFoundException;
import com.airtribe.smart_parking_lot_system.model.dto.EntryRequest;
import com.airtribe.smart_parking_lot_system.model.dto.EntryResponse;
import com.airtribe.smart_parking_lot_system.model.dto.ExitResponse;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.entity.Ticket;
import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.TicketRepository;
import com.airtribe.smart_parking_lot_system.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Business flow in isolation: repositories and collaborating services are mocked, the clock is fixed. */
@ExtendWith(MockitoExtension.class)
class ParkingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String PLATE = "TS09AB1234";

    @Mock
    VehicleRepository vehicles;
    @Mock
    TicketRepository tickets;
    @Mock
    SpotAllocationService allocation;
    @Mock
    ParkingFeeService feeService;

    ParkingService service;

    @BeforeEach
    void setUp() {
        service = new ParkingService(vehicles, tickets, allocation, feeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Vehicle vehicle() {
        Vehicle v = new Vehicle(PLATE, VehicleType.CAR);
        v.setId(1L);
        return v;
    }

    private static ParkingSpot spot() {
        ParkingSpot s = new ParkingSpot(2, 5, SpotType.CAR);
        s.setId(10L);
        return s;
    }

    /** A ticket for a car that entered 61 minutes before NOW. Non-active tickets are already checked out. */
    private static Ticket ticket(TicketStatus status) {
        Ticket t = new Ticket();
        t.setVehicle(vehicle());
        t.setSpot(spot());
        t.setEntryTime(NOW.minusSeconds(3660));
        t.setStatus(status);
        if (status != TicketStatus.ACTIVE) {
            t.setExitTime(NOW.minusSeconds(60));
            t.setFeeAmount(new BigDecimal("60.00"));
        }
        return t;
    }

    private void stubLockedTicket(Ticket t) {
        when(tickets.findByTicketCodeForUpdate(t.getTicketCode())).thenReturn(Optional.of(t));
    }

    // ================= enter =================

    @Test
    void enter_newVehicle_createsTicketAndLinksBothSides() {
        Vehicle vehicle = vehicle();
        ParkingSpot spot = spot();
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(vehicle));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.empty());
        when(allocation.allocate(VehicleType.CAR)).thenReturn(spot);

        EntryResponse response = service.enter(new EntryRequest(PLATE, VehicleType.CAR));

        verify(vehicles).insertIfAbsent(PLATE, "CAR");
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(captor.capture());
        Ticket saved = captor.getValue();
        assertThat(saved.getVehicle()).isSameAs(vehicle);
        assertThat(saved.getSpot()).isSameAs(spot);
        assertThat(saved.getEntryTime()).isEqualTo(NOW);
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(vehicle.getTickets()).containsExactly(saved);
        assertThat(spot.getTickets()).containsExactly(saved);

        assertThat(response.ticketCode()).isEqualTo(saved.getTicketCode());
        assertThat(response.floorNumber()).isEqualTo(2);
        assertThat(response.spotNumber()).isEqualTo(5);
        assertThat(response.entryTime()).isEqualTo(NOW);
        assertThat(response.existingTicket()).isFalse();
    }

    @Test
    void enter_normalisesThePlateBeforeTouchingTheDatabase() {
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(vehicle()));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.empty());
        when(allocation.allocate(VehicleType.CAR)).thenReturn(spot());

        service.enter(new EntryRequest(" ts09-ab 1234 ", VehicleType.CAR));

        verify(vehicles).insertIfAbsent(PLATE, "CAR");
        verify(vehicles).findByPlateNumberForUpdate(PLATE);
    }

    @Test
    void enter_returningVehicleWithNewType_allocatesByTheRequestedType() {
        Vehicle vehicle = vehicle();   // stored as CAR
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(vehicle));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.empty());
        when(allocation.allocate(VehicleType.BUS)).thenReturn(spot());

        service.enter(new EntryRequest(PLATE, VehicleType.BUS));

        assertThat(vehicle.getVehicleType()).isEqualTo(VehicleType.BUS);
        verify(allocation).allocate(VehicleType.BUS);
        verify(allocation, never()).allocate(VehicleType.CAR);
    }

    @Test
    void enter_vehicleAlreadyParked_returnsTheExistingTicket_withoutAllocating() {
        Ticket open = ticket(TicketStatus.ACTIVE);
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(open.getVehicle()));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.of(open));

        EntryResponse response = service.enter(new EntryRequest(PLATE, VehicleType.CAR));

        assertThat(response.existingTicket()).isTrue();
        assertThat(response.ticketCode()).isEqualTo(open.getTicketCode());
        assertThat(response.floorNumber()).isEqualTo(2);
        verifyNoInteractions(allocation);
        verify(tickets, never()).save(any());
    }

    @Test
    void enter_unpaidTicketOutstanding_isRejected() {
        Ticket pending = ticket(TicketStatus.PAYMENT_PENDING);
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(pending.getVehicle()));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.enter(new EntryRequest(PLATE, VehicleType.CAR)))
                .isInstanceOf(PaymentPendingException.class);

        verifyNoInteractions(allocation);
        verify(tickets, never()).save(any());
    }

    @Test
    void enter_noSpotAvailable_propagates_andSavesNothing() {
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.of(vehicle()));
        when(tickets.findOpenByVehicleId(eq(1L), anyCollection())).thenReturn(Optional.empty());
        when(allocation.allocate(VehicleType.CAR)).thenThrow(new NoSpotAvailableException("full"));

        assertThatThrownBy(() -> service.enter(new EntryRequest(PLATE, VehicleType.CAR)))
                .isInstanceOf(NoSpotAvailableException.class);

        verify(tickets, never()).save(any());
    }

    @Test
    void enter_invalidPlate_isRejectedBeforeAnyRepositoryCall() {
        assertThatThrownBy(() -> service.enter(new EntryRequest("---", VehicleType.CAR)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(vehicles, tickets, allocation);
    }

    @Test
    void enter_vehicleRowMissingAfterUpsert_failsLoudly() {
        when(vehicles.findByPlateNumberForUpdate(PLATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enter(new EntryRequest(PLATE, VehicleType.CAR)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ================= exit =================

    @Test
    void exit_unknownTicket_throwsNotFound() {
        UUID code = UUID.randomUUID();
        when(tickets.findByTicketCodeForUpdate(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exit(code)).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void exit_activeTicketWithFee_freezesFeeAndWaitsForPayment() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        stubLockedTicket(t);
        when(feeService.calculateFee(VehicleType.CAR, t.getEntryTime(), NOW)).thenReturn(new BigDecimal("60.00"));

        ExitResponse r = service.exit(t.getTicketCode());

        assertThat(r.status()).isEqualTo(TicketStatus.PAYMENT_PENDING);
        assertThat(r.fee()).isEqualByComparingTo("60.00");
        assertThat(r.exitTime()).isEqualTo(NOW);
        assertThat(r.durationMinutes()).isEqualTo(61);
        assertThat(r.plateNumber()).isEqualTo(PLATE);
        verify(allocation, never()).release(any());   // vehicle is still inside until it pays
    }

    @Test
    void exit_activeTicketWithZeroFee_closesImmediately_andFreesTheSpot() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        stubLockedTicket(t);
        when(feeService.calculateFee(VehicleType.CAR, t.getEntryTime(), NOW))
                .thenReturn(BigDecimal.ZERO.setScale(2));

        ExitResponse r = service.exit(t.getTicketCode());

        assertThat(r.status()).isEqualTo(TicketStatus.CLOSED);
        verify(allocation).release(t.getSpot());
    }

    @Test
    void exit_secondCall_returnsTheSameReceipt_withoutRecalculating() {
        Ticket t = ticket(TicketStatus.PAYMENT_PENDING);
        stubLockedTicket(t);

        ExitResponse r = service.exit(t.getTicketCode());

        assertThat(r.fee()).isEqualByComparingTo("60.00");
        assertThat(r.exitTime()).isEqualTo(NOW.minusSeconds(60));
        verifyNoInteractions(feeService, allocation);
    }

    @Test
    void exit_alreadyClosedTicket_isIdempotent() {
        Ticket t = ticket(TicketStatus.CLOSED);
        stubLockedTicket(t);

        assertThat(service.exit(t.getTicketCode()).status()).isEqualTo(TicketStatus.CLOSED);
        verifyNoInteractions(feeService, allocation);
    }

    // ================= exitByPlate =================

    @Test
    void exitByPlate_normalisesPlate_andChecksOut() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        when(tickets.findOpenByPlateNumberForUpdate(eq(PLATE), anyCollection())).thenReturn(Optional.of(t));
        when(feeService.calculateFee(any(), any(), any())).thenReturn(BigDecimal.ZERO.setScale(2));

        ExitResponse r = service.exitByPlateNumber("  ts09ab1234 ");

        assertThat(r.status()).isEqualTo(TicketStatus.CLOSED);
        verify(tickets).findOpenByPlateNumberForUpdate(eq(PLATE), anyCollection());
    }

    @Test
    void exitByPlate_noOpenTicket_throwsNotFound() {
        when(tickets.findOpenByPlateNumberForUpdate(eq(PLATE), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exitByPlateNumber(PLATE)).isInstanceOf(TicketNotFoundException.class);
    }

    // ================= pay =================

    @Test
    void pay_unknownTicket_throwsNotFound() {
        UUID code = UUID.randomUUID();
        when(tickets.findByTicketCodeForUpdate(code)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(code, "REF")).isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void pay_beforeCheckout_isRejected() {
        Ticket t = ticket(TicketStatus.ACTIVE);
        stubLockedTicket(t);

        assertThatThrownBy(() -> service.pay(t.getTicketCode(), "REF"))
                .isInstanceOf(InvalidTicketStateException.class);

        verifyNoInteractions(allocation);
    }

    @Test
    void pay_pendingTicket_recordsPayment_closesTicket_andFreesTheSpot() {
        Ticket t = ticket(TicketStatus.PAYMENT_PENDING);
        stubLockedTicket(t);

        ExitResponse r = service.pay(t.getTicketCode(), "UPI-123");

        assertThat(r.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(t.getPaymentReference()).isEqualTo("UPI-123");
        assertThat(t.getPaidAt()).isEqualTo(NOW);
        verify(allocation).release(t.getSpot());
    }

    @Test
    void pay_alreadyClosed_isIdempotent_andKeepsTheOriginalReference() {
        Ticket t = ticket(TicketStatus.CLOSED);
        t.setPaymentReference("FIRST");
        stubLockedTicket(t);

        ExitResponse r = service.pay(t.getTicketCode(), "SECOND");

        assertThat(r.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(t.getPaymentReference()).isEqualTo("FIRST");
        verifyNoInteractions(allocation);
    }
}
