package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    /** Race-free upsert: concurrent first-time entries for the same plate cannot fail on the unique key. */
    @Modifying
    @Query(value = "insert into vehicle (plateNumber, vehicleType) values (:plateNumber, :vehicleType) "
            + "on conflict (plateNumber) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("plateNumber") String plateNumber, @Param("vehicleType") String vehicleType);

    /** Row lock that serialises every entry for the same plate. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.plateNumber = :plateNumber")
    Optional<Vehicle> findByPlateNumberForUpdate(@Param("plateNumber") String plateNumber);
}
