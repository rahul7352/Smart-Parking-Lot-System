package com.airtribe.smart_parking_lot_system.config;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import com.airtribe.smart_parking_lot_system.repository.PricingRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Seeds 3 floors (2 motorcycle, 6 car, 2 bus per floor) and pricing on first run. */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final ParkingSpotRepository spots;
    private final PricingRuleRepository rules;

    public DataSeeder(ParkingSpotRepository spots, PricingRuleRepository rules) {
        this.spots = spots;
        this.rules = rules;
    }

    @Override
    public void run(String... args) {
        if (spots.count() == 0) {
            List<ParkingSpot> all = new ArrayList<>();
            for (int floor = 1; floor <= 3; floor++) {
                int n = 1;
                for (int i = 0; i < 2; i++) all.add(new ParkingSpot(floor, n++, SpotType.MOTORCYCLE));
                for (int i = 0; i < 6; i++) all.add(new ParkingSpot(floor, n++, SpotType.CAR));
                for (int i = 0; i < 2; i++) all.add(new ParkingSpot(floor, n++, SpotType.BUS));
            }
            spots.saveAll(all);
        }
        if (rules.count() == 0) {
            rules.saveAll(List.of(
                    new PricingRule(VehicleType.MOTORCYCLE, new BigDecimal("10.00"), 15),
                    new PricingRule(VehicleType.CAR, new BigDecimal("30.00"), 15),
                    new PricingRule(VehicleType.BUS, new BigDecimal("80.00"), 15)));
        }
    }
}
