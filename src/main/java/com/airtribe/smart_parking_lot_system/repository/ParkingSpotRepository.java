package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.ParkingSpot;
import com.airtribe.smart_parking_lot_system.model.enums.SpotStatus;
import com.airtribe.smart_parking_lot_system.model.enums.SpotType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    /**
     * ORDER BY floor, spot LIMIT 1 FOR UPDATE SKIP LOCKED.
     * Lock timeout -2 = Hibernate's SKIP_LOCKED: concurrent entries never block each other
     * and never receive the same spot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
            """
            select s from ParkingSpot s
            where s.spotType = :type and s.status = :status
            order by s.floorNumber, s.spotNumber
            """
    )
    List<ParkingSpot> lockNextFree(@Param("type") SpotType type,
                                   @Param("status") SpotStatus status, Pageable pageable);

    /** Plain read (ignores row locks): "is the lot really full, or are free spots just locked?" */
    boolean existsBySpotTypeInAndStatus(Collection<SpotType> types, SpotStatus status);

    @Query("""
            select s.floorNumber, s.spotType, count(case when s.status = :freeStatus then 1 end)
            from ParkingSpot s
            group by s.floorNumber, s.spotType
            order by s.floorNumber
            """)
    List<Object[]> freeStatusCountsByFloorAndType(@Param("freeStatus") SpotStatus freeStatus);

    @Query("select coalesce(max(s.spotNumber), 0) from ParkingSpot s where s.floorNumber = :floorNumber")
    int maxSpotNumber(@Param("floorNumber") int floorNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpot s where id = :id")
    Optional<ParkingSpot> findByIdForUpdate(@Param("id") Long id);
}
