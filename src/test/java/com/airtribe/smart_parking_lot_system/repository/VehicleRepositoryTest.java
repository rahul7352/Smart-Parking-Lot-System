package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import com.airtribe.smart_parking_lot_system.model.enums.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleRepositoryTest extends AbstractRepositoryTest {

    @Autowired VehicleRepository vehicles;

    @Test
    void insertIfAbsent_insertsOnce_andIgnoresTheSecondCall() {
        int first = vehicles.insertIfAbsent("TS09AB1234", "CAR");
        int second = vehicles.insertIfAbsent("TS09AB1234", "BUS");   // conflict: ignored, type unchanged
        em.clear();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(vehicles.count()).isEqualTo(1);
        assertThat(vehicles.findByPlateNumber("TS09AB1234").orElseThrow().getVehicleType())
                .isEqualTo(VehicleType.CAR);
    }

    @Test
    void findByPlateNo_andForUpdateVariant_findExistingVehicles() {
        em.persistAndFlush(new Vehicle("KA01XY9999", VehicleType.BUS));

        assertThat(vehicles.findByPlateNumber("KA01XY9999")).isPresent();
        assertThat(vehicles.findByPlateNumberForUpdate("KA01XY9999")).isPresent();
        assertThat(vehicles.findByPlateNumber("UNKNOWN1")).isEmpty();
        assertThat(vehicles.findByPlateNumberForUpdate("UNKNOWN1")).isEmpty();
    }

    @Test
    void plateNo_mustBeUnique() {
        vehicles.saveAndFlush(new Vehicle("TS09AB1234", VehicleType.CAR));

        assertThatThrownBy(() -> vehicles.saveAndFlush(new Vehicle("TS09AB1234", VehicleType.CAR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
