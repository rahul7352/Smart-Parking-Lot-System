package com.airtribe.smart_parking_lot_system.controller;

import com.airtribe.smart_parking_lot_system.model.dto.AddSpotRequest;
import com.airtribe.smart_parking_lot_system.model.dto.SpotResponse;
import com.airtribe.smart_parking_lot_system.model.dto.SpotStatusRequest;
import com.airtribe.smart_parking_lot_system.service.SpotAdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff-only operations. Authentication/authorisation is intentionally out of scope for this brief. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private SpotAdminService adminService;

    @PostMapping("/spots")
    public ResponseEntity<List<SpotResponse>> addSpots(@Valid @RequestBody AddSpotRequest addSpotRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addSpots(addSpotRequest));
    }

    @PatchMapping("/spots/{id}/status")
    public ResponseEntity<SpotResponse> setStatus(@PathVariable Long id, @Valid @RequestBody SpotStatusRequest statusRequest) {
        return ResponseEntity.ok(adminService.setStatus(id, statusRequest.status()));
    }
}
