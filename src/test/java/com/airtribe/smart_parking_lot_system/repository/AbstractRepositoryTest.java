package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.support.SharedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * JPA slice on a real Postgres: the repositories rely on Postgres features (SKIP LOCKED,
 * ON CONFLICT, partial indexes), so H2 would hide real bugs. Each test runs in a transaction
 * that is rolled back, and starts with the tables emptied inside that transaction.
 */
@Tag("docker")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry);
    }

    @Autowired
    protected TestEntityManager em;

    @BeforeEach
    void emptyTables() {
        em.getEntityManager().createQuery("delete from Ticket").executeUpdate();
        em.getEntityManager().createQuery("delete from Vehicle").executeUpdate();
        em.getEntityManager().createQuery("delete from ParkingSpot").executeUpdate();
        em.clear();
    }
}
