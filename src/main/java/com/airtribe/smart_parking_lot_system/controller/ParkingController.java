package com.airtribe.smart_parking_lot_system.controller;

import com.airtribe.smart_parking_lot_system.model.dto.*;
import com.airtribe.smart_parking_lot_system.service.AvailabilityService;
import com.airtribe.smart_parking_lot_system.service.ParkingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/v1/parking")
public class ParkingController {

    @Autowired
    private ParkingService parkingService;

    @Autowired
    private AvailabilityService availabilityService;

    /** 201 for a new ticket, 200 when the vehicle was already parked (idempotent retry). */
    @PostMapping("/entry")
    public ResponseEntity<EntryResponse> enter(@Valid @RequestBody EntryRequest entryRequest) {
        EntryResponse response = parkingService.enter((entryRequest));
        return ResponseEntity.status(response.existingTicket() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @PostMapping("/exit/{ticketCode}")
    public ExitResponse exit(@PathVariable UUID ticketCode) {
        return parkingService.exit(ticketCode);
    }

    /** Lost-ticket exit (staff). */
    @PostMapping("/exit/by-plate")
    public ExitResponse exitByPlate(@Valid @RequestBody LostTicketExitRequest lostTicketExitRequest) {
        return parkingService.exitByPlateNumber(lostTicketExitRequest.plateNumber());
    }

    @PostMapping("/tickets/{ticketCode}/pay")
    public ExitResponse pay(@PathVariable UUID ticketCode, @Valid @RequestBody PayRequest payRequest) {
        return parkingService.pay(ticketCode, payRequest.paymentReference());
    }

    @GetMapping("/availability")
    public AvailabilityResponse availability() {
        return availabilityService.availability();
    }
}
