package com.airtribe.smart_parking_lot_system.model.entity;

import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    /**
     * Inverse side: Ticket.vehicle owns the foreign key, so this adds no column or join table.
     * No cascade / orphanRemoval: tickets are billing records and must outlive list changes.
     * Avoid iterating this in hot paths; it grows with every visit. Prefer a paged repository query.
     */
    @OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY)
    private List<Ticket> tickets = new ArrayList<>();

    /** Keeps both sides in sync; only Ticket.vehicle is persisted. */
    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setVehicle(this);
    }

    public Vehicle(String plateNumber, VehicleType vehicleType) {
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
    }
}
