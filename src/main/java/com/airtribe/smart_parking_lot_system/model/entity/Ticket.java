package com.airtribe.smart_parking_lot_system.model.entity;

import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket",
        indexes = @Index(name = "idx_ticket_vehicle_status", columnList = "vehicle_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque public identifier; the numeric id is never exposed. */
    @Column(name = "ticket_code", nullable = false, unique = true, updatable = false)
    private UUID ticketCode = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id")
    private ParkingSpot spot;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.ACTIVE;

    @Column(name = "fee_amount", precision = 12, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "paid_at")
    private Instant paidAt;
}
