package com.airtribe.smart_parking_lot_system.config;

import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.PricingRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Startup validation: the app must refuse to start with an incomplete rate card. */
@ExtendWith(MockitoExtension.class)
class PricingConfigValidatorTest {

    @Mock
    PricingRuleRepository rules;

    private static PricingRule rule(VehicleType t) {
        return new PricingRule(t, new BigDecimal("10.00"), 15);
    }

    @Test
    void missingRule_failsStartup_andNamesTheVehicleType() {
        when(rules.findAll()).thenReturn(List.of(rule(VehicleType.CAR), rule(VehicleType.MOTORCYCLE)));
        assertThatThrownBy(() -> new PricingConfigValidator(rules).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUS");
    }

    @Test
    void completeRateCard_passes() {
        when(rules.findAll()).thenReturn(List.of(
                rule(VehicleType.CAR), rule(VehicleType.MOTORCYCLE), rule(VehicleType.BUS)));
        assertThatCode(() -> new PricingConfigValidator(rules).validate()).doesNotThrowAnyException();
    }
}
