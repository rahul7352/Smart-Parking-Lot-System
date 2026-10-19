package com.airtribe.smart_parking_lot_system.model.entity;

import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pricing_rule")
public class PricingRule {

    @Id
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    @Column(name = "rate_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerHour;

    @Column(name = "free_minutes", nullable = false)
    private int freeMinutes;

    public PricingRule(VehicleType vehicleType, BigDecimal ratePerHour, int freeMinutes) {
        this.vehicleType = vehicleType;
        this.ratePerHour = ratePerHour;
        this.freeMinutes = freeMinutes;
    }
}
