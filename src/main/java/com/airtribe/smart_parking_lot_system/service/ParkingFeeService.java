package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ParkingFeeService {

    private final PricingRuleRepository pricingRuleRepository;

    /**
     * Whole-second precision: free while stay <= grace period, then ceil(seconds / 3600) * rate.
     * e.g. grace 15 min: 15:00 -> free, 15:01 -> 1 hour; 60:00 -> 1 hour, 60:01 -> 2 hours.
     */
    public BigDecimal calculateFee(VehicleType vehicleType, Instant entry, Instant exit) {
        PricingRule rule = pricingRuleRepository.findById(vehicleType)
                .orElseThrow(() -> new IllegalStateException("No pricing rule found for " + vehicleType));

        Duration stay = Duration.between(entry, exit);

        if(stay.isNegative()) {
            return BigDecimal.ZERO.setScale(2);
        }

        long seconds = stay.getSeconds() + (stay.getNano() > 0 ? 1 : 0);
        if(seconds <= rule.getFreeMinutes() * 60L) {
            return BigDecimal.ZERO.setScale(2);
        }

        long billableHours = (seconds + 3599) / 3600;
        return rule.getRatePerHour().multiply(BigDecimal.valueOf(billableHours));
    }
}
