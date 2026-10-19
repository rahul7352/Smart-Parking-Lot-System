package com.airtribe.smart_parking_lot_system.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlateNumbersTest {

    @Test
    void normalisesCaseWhitespaceAndHyphens() {
        assertThat(PlateNumbers.normalize(" ts09-ab 1234 ")).isEqualTo("TS09AB1234");
        assertThat(PlateNumbers.normalize("TS09AB1234")).isEqualTo("TS09AB1234");
    }

    @Test
    void rejectsPlatesThatAreEmptyShortOrContainSymbols() {
        assertThatThrownBy(() -> PlateNumbers.normalize("---")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlateNumbers.normalize("AB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlateNumbers.normalize("TS09@AB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlateNumbers.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
