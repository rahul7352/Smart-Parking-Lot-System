package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.exception.InvalidTicketStateException;
import com.airtribe.smart_parking_lot_system.exception.PaymentPendingException;
import com.airtribe.smart_parking_lot_system.exception.TicketNotFoundException;
import com.airtribe.smart_parking_lot_system.model.dto.EntryRequest;
import com.airtribe.smart_parking_lot_system.model.dto.EntryResponse;
import com.airtribe.smart_parking_lot_system.model.dto.ExitResponse;
import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.entity.Ticket;
import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import com.airtribe.smart_parking_lot_system.repository.TicketRepository;
import com.airtribe.smart_parking_lot_system.repository.VehicleRepository;
import com.airtribe.smart_parking_lot_system.util.PlateNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ParkingService {

    private static final List<TicketStatus> OPEN = List.of(TicketStatus.ACTIVE, TicketStatus.PAYMENT_PENDING);

    private final VehicleRepository vehicleRepository;
    private final TicketRepository ticketRepository;
    private final SpotAllocationService allocationService;
    private final ParkingFeeService feeService;
    private final Clock clock;

    /**
     * Idempotent entry. The vehicle row lock serialises all entries for one plate, which makes the
     * "already parked?" check race-free. A retried request gets the existing ticket back.
     */
    @Transactional
    public EntryResponse enter(EntryRequest request) {
        String plateNumber = PlateNumbers.normalize(request.plateNumber());
        vehicleRepository.insertIfAbsent(plateNumber, request.vehicleType().name());
        Vehicle vehicle = vehicleRepository.findByPlateNumberForUpdate(plateNumber)
                .orElseThrow(() -> new IllegalStateException("Vehicle row missing after row upsert"));

        Ticket openTicket = ticketRepository.findOpenByVehicleId(vehicle.getId(), OPEN).orElse(null);
        if(openTicket != null) {
            if(openTicket.getStatus() == TicketStatus.PAYMENT_PENDING) {
                throw new PaymentPendingException(plateNumber);
            }
            return toEntryResponse(openTicket, true);
        }
        vehicle.setVehicleType(request.vehicleType()); // the vehicle type presented now decides the parking spot
        ParkingSpot parkingSpot = allocationService.allocate(request.vehicleType());

        Ticket ticket = new Ticket();
        vehicle.addTicket(ticket);
        parkingSpot.addTicket(ticket);
        ticket.setEntryTime(Instant.now(clock));
        ticketRepository.save(ticket);
        return toEntryResponse(ticket, false);

    }

    /** Checkout: freezes the fee. Zero fee closes immediately; otherwise waits for payment. */
    @Transactional
    public ExitResponse exit(UUID ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCodeForUpdate(ticketCode)
                .orElseThrow(() -> new TicketNotFoundException(ticketCode.toString()));
        return checkout(ticket);
    }

    /** Lost-ticket flow. Intended for staff use; protect with authorisation in production. */
    @Transactional
    public ExitResponse exitByPlateNumber(String plateNumber) {
        String plateNo = PlateNumbers.normalize(plateNumber);
        Ticket ticket = ticketRepository.findOpenByPlateNumberForUpdate(plateNo, OPEN)
                .orElseThrow(() -> new TicketNotFoundException(plateNo));
        return checkout(ticket);
    }

    /** Settles a PAYMENT_PENDING ticket, closes it and frees the spot. Idempotent once closed. */
    @Transactional
    public ExitResponse pay(UUID ticketCode, String paymentReference) {
        Ticket ticket = ticketRepository.findByTicketCodeForUpdate(ticketCode)
                .orElseThrow(() -> new TicketNotFoundException(ticketCode.toString()));
        if(ticket.getStatus() == TicketStatus.CLOSED) {
            return toExitResponse(ticket);
        }
        if(ticket.getStatus() == TicketStatus.ACTIVE) {
            throw new InvalidTicketStateException("Ticket must be checked out (exit) before payment");
        }
        ticket.setPaymentReference(paymentReference);
        ticket.setPaidAt(Instant.now(clock));
        ticket.setStatus(TicketStatus.CLOSED);
        allocationService.release(ticket.getSpot());
        return toExitResponse(ticket);
    }

    private ExitResponse checkout(Ticket ticket) {
        if(ticket.getStatus() == TicketStatus.ACTIVE) {
            Instant now = Instant.now(clock);
            BigDecimal fee = feeService.calculateFee(ticket.getVehicle().getVehicleType(),
                    ticket.getEntryTime(), now);
            ticket.setExitTime(now);
            ticket.setFeeAmount(fee);
            if(fee.signum() == 0) {
                ticket.setStatus(TicketStatus.CLOSED);
                allocationService.release(ticket.getSpot());
            } else {
                ticket.setStatus(TicketStatus.PAYMENT_PENDING);
            }
        }
        return toExitResponse(ticket);
    }

    private EntryResponse toEntryResponse(Ticket ticket, boolean existing) {
        ParkingSpot parkingSpot = ticket.getSpot();
        return new EntryResponse(ticket.getTicketCode(), parkingSpot.getFloorNumber(),
                parkingSpot.getSpotNumber(), ticket.getEntryTime(), existing);
    }

    private ExitResponse toExitResponse(Ticket ticket) {
        long minutes = Duration.between(ticket.getEntryTime(), ticket.getExitTime()).toMinutes();
        return new ExitResponse(ticket.getTicketCode(), ticket.getVehicle().getPlateNumber(), ticket.getEntryTime(),
                ticket.getExitTime(), minutes, ticket.getFeeAmount(), ticket.getStatus());
    }

}
