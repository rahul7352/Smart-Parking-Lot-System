package com.airtribe.smart_parking_lot_system.repository;

import com.airtribe.smart_parking_lot_system.model.entity.Ticket;
import com.airtribe.smart_parking_lot_system.model.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("select t from Ticket t where t.vehicle.id = :vehicleId and t.status in :statuses")
    Optional<Ticket> findOpenByVehicleId(@Param("vehicleId") Long vehicleId, @Param("statuses")Collection<TicketStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.ticketCode = :ticketCode")
    Optional<Ticket> findByTicketCodeForUpdate(@Param("ticketCode")UUID ticketCode);

    /** Lost-ticket lookup. Subquery (not a join) so only the ticket row is locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from Ticket t
            where t.vehicle.id = (select v.id from Vehicle v where v.plateNumber = :plateNumber)
            and t.status in :statuses
            """)
    Optional<Ticket> findOpenByPlateNumberForUpdate(@Param("platerNumber") String plateNumber,
                                                     @Param("statuses") Collection<TicketStatus> statuses);
}
