package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.entity.Ticket;
import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketRepositoryTest extends AbstractRepositoryTest {

    private static final List<TicketStatus> OPEN = List.of(TicketStatus.ACTIVE, TicketStatus.PAYMENT_PENDING);

    @Autowired TicketRepository tickets;

    private Vehicle vehicle(String plate) {
        return em.persistAndFlush(new Vehicle(plate, VehicleType.CAR));
    }

    private ParkingSpot spot(int number) {
        return em.persistAndFlush(new ParkingSpot(1, number, SpotType.CAR));
    }

    private Ticket newTicket(Vehicle v, ParkingSpot s, TicketStatus status) {
        Ticket t = new Ticket();
        t.setVehicle(v);
        t.setSpot(s);
        t.setEntryTime(Instant.parse("2026-10-15T10:00:00Z"));
        t.setStatus(status);
        return t;
    }

    private Ticket saved(Vehicle v, ParkingSpot s, TicketStatus status) {
        return em.persistAndFlush(newTicket(v, s, status));
    }

    @Test
    void ticketCode_isGeneratedAndUnique() {
        Vehicle v = vehicle("TS09AB1234");
        Ticket a = saved(v, spot(1), TicketStatus.CLOSED);
        Ticket b = saved(v, spot(2), TicketStatus.CLOSED);

        assertThat(a.getTicketCode()).isNotNull().isNotEqualTo(b.getTicketCode());
    }

    @Test
    void findOpenByVehicleId_returnsActiveAndPaymentPendingTickets_butNotClosed() {
        Vehicle active = vehicle("TS09AB1234");
        Vehicle pending = vehicle("KA01XY9999");
        Vehicle closed = vehicle("MH12CD5678");
        saved(active, spot(1), TicketStatus.ACTIVE);
        saved(pending, spot(2), TicketStatus.PAYMENT_PENDING);
        saved(closed, spot(3), TicketStatus.CLOSED);

        assertThat(tickets.findOpenByVehicleId(active.getId(), OPEN)).isPresent();
        assertThat(tickets.findOpenByVehicleId(pending.getId(), OPEN)).isPresent();
        assertThat(tickets.findOpenByVehicleId(closed.getId(), OPEN)).isEmpty();
    }

    @Test
    void findByTicketCodeForUpdate_findsTicketOrEmpty() {
        Ticket t = saved(vehicle("TS09AB1234"), spot(1), TicketStatus.ACTIVE);

        assertThat(tickets.findByTicketCodeForUpdate(t.getTicketCode())).isPresent();
        assertThat(tickets.findByTicketCodeForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findOpenByPlateForUpdate_findsOnlyTheOpenTicketOfThatPlate() {
        Vehicle v = vehicle("TS09AB1234");
        saved(v, spot(1), TicketStatus.CLOSED);
        Ticket open = saved(v, spot(2), TicketStatus.ACTIVE);
        saved(vehicle("KA01XY9999"), spot(3), TicketStatus.ACTIVE);

        assertThat(tickets.findOpenByPlateNumberForUpdate("TS09AB1234", OPEN))
                .get().extracting(Ticket::getTicketCode).isEqualTo(open.getTicketCode());
        assertThat(tickets.findOpenByPlateNumberForUpdate("UNKNOWN1", OPEN)).isEmpty();
    }

    @Test
    void findOpenByPlateForUpdate_ignoresVehiclesWhoseTicketsAreAllClosed() {
        saved(vehicle("TS09AB1234"), spot(1), TicketStatus.CLOSED);

        assertThat(tickets.findOpenByPlateNumberForUpdate("TS09AB1234", OPEN)).isEmpty();
    }

    // Relies on schema.sql (unique partial index), which Spring Boot runs after Hibernate creates the tables.
    @Test
    void partialUniqueIndex_rejectsSecondOpenTicketForSameVehicle() {
        Vehicle v = vehicle("TS09AB1234");
        ParkingSpot s1 = spot(1);
        ParkingSpot s2 = spot(2);
        saved(v, s1, TicketStatus.ACTIVE);

        assertThatThrownBy(() -> tickets.saveAndFlush(newTicket(v, s2, TicketStatus.PAYMENT_PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void partialUniqueIndex_allowsNewOpenTicketOnceEarlierOnesAreClosed() {
        Vehicle v = vehicle("TS09AB1234");
        saved(v, spot(1), TicketStatus.CLOSED);
        saved(v, spot(2), TicketStatus.CLOSED);

        Ticket open = tickets.saveAndFlush(newTicket(v, spot(3), TicketStatus.ACTIVE));

        assertThat(open.getId()).isNotNull();
    }
}
