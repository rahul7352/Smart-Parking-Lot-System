package com.airtribe.smart_parking_lot_system.integration;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import com.airtribe.smart_parking_lot_system.repository.ParkingSpotRepository;
import com.airtribe.smart_parking_lot_system.repository.TicketRepository;
import com.airtribe.smart_parking_lot_system.repository.VehicleRepository;
import com.airtribe.smart_parking_lot_system.support.SharedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.stream.IntStream;

/**
 * Full application context on a real Postgres (needs Docker). Every test starts from empty
 * spots/vehicles/tickets (pricing rules stay seeded) and a reset clock.
 */
@Tag("docker")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
abstract class AbstractPostgresTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry);
    }

    @Autowired protected ParkingSpotRepository spotRepository;
    @Autowired protected TicketRepository ticketRepository;
    @Autowired protected VehicleRepository vehicleRepository;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void wipe() {
        ticketRepository.deleteAll();
        vehicleRepository.deleteAll();
        spotRepository.deleteAll();
        clock.reset();
    }

    protected void seedSpots(int floor, SpotType type, int count) {
        int start = spotRepository.maxSpotNumber(floor) + 1;
        spotRepository.saveAll(IntStream.range(0, count)
                .mapToObj(i -> new ParkingSpot(floor, start + i, type))
                .toList());
    }
}
