package com.airtribe.smart_parking_lot_system.config;

import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.PricingRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(2)
public class PricingConfigValidator implements CommandLineRunner {

    private final PricingRuleRepository pricingRuleRepository;

    public PricingConfigValidator(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        validate();
    }

    public void validate() {
        Set<VehicleType> configuredVehicleType = pricingRuleRepository.findAll().stream()
                .map(PricingRule::getVehicleType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(VehicleType.class)));

        List<VehicleType> missingVehicleType = Arrays.stream(VehicleType.values())
                .filter(t -> !configuredVehicleType.contains(t))
                .toList();

        if(!missingVehicleType.isEmpty()) {
            throw new IllegalStateException("Missing pricing rule for vehicle types: " + missingVehicleType);
        }
    }
}
