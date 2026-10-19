package com.airtribe.smart_parking_lot_system.model.entity;

import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parking_spot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"floor_number", "spot_number"}),
        indexes = @Index(name = "idx_spot_allc", columnList = "spot_type, status, floor_number, spot_number"))
@Getter
@Setter
@NoArgsConstructor
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    @Column(name = "spot_number", nullable = false)
    private int spotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "spot_type", nullable = false)
    private SpotType spotType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpotStatus status = SpotStatus.FREE;

    @Version
    private Long version;

    /**
     * Inverse side: Ticket.spot owns the foreign key, so no extra column or join table.
     * Inverse collections do not bump @Version, so adding a ticket never causes a spurious
     * optimistic-lock conflict on the spot. No cascade / orphanRemoval (tickets are billing records).
     * A spot accumulates a ticket per visit, so never iterate this in request paths.
     */
    @OneToMany(mappedBy = "spot", fetch = FetchType.LAZY)
    private List<Ticket> tickets = new ArrayList<>();

    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setSpot(this);
    }

    public ParkingSpot(int floorNumber, int spotNumber, SpotType spotType) {
        this.floorNumber = floorNumber;
        this.spotNumber = spotNumber;
        this.spotType = spotType;
    }
}
