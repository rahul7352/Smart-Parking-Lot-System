package com.airtribe.smart_parking_lot_system.service;

import com.airtribe.smart_parking_lot_system.model.entity.PricingRule;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import com.airtribe.smart_parking_lot_system.repository.PricingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/** Boundary tests for the fee rules. No database needed. Rule: CAR, 30.00/hour, 15 free minutes. */
@ExtendWith(MockitoExtension.class)
class ParkingFeeServiceTest {

    private static final Instant ENTRY = Instant.parse("2026-10-15T10:00:00Z");

    @Mock
    PricingRuleRepository rules;
    @InjectMocks
    ParkingFeeService feeService;

    @BeforeEach
    void rule() {
        lenient().when(rules.findById(VehicleType.CAR))
                .thenReturn(Optional.of(new PricingRule(VehicleType.CAR, new BigDecimal("30.00"), 15)));
    }

    @ParameterizedTest(name = "{0}s parked -> {1}")
    @CsvSource({
            "0,      0.00",
            "899,    0.00",
            "900,    0.00",    // exactly 15:00 is still free
            "901,    30.00",   // 15:01 starts hour 1
            "3599,   30.00",
            "3600,   30.00",   // exactly 60:00 is one hour
            "3601,   60.00",   // 60:01 starts hour 2 (previously rounded down)
            "7200,   60.00",
            "7201,   90.00",
            "86400,  720.00"
    })
    void feeAtBoundaries(long seconds, String expected) {
        BigDecimal fee = feeService.calculateFee(VehicleType.CAR, ENTRY, ENTRY.plusSeconds(seconds));
        assertThat(fee).isEqualByComparingTo(expected);
    }

    @Test
    void subSecondOvershootOfGracePeriod_isBilled() {
        Instant exit = ENTRY.plusSeconds(900).plusNanos(1);
        assertThat(feeService.calculateFee(VehicleType.CAR, ENTRY, exit)).isEqualByComparingTo("30.00");
    }

    @Test
    void exitBeforeEntry_clockSkew_isFree() {
        assertThat(feeService.calculateFee(VehicleType.CAR, ENTRY, ENTRY.minusSeconds(30)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void missingPricingRule_failsLoudly() {
        lenient().when(rules.findById(VehicleType.BUS)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> feeService.calculateFee(VehicleType.BUS, ENTRY, ENTRY.plusSeconds(3600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUS");
    }
}
