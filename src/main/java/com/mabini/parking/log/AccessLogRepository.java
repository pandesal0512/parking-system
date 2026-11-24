package com.mabini.parking.log;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    boolean existsByVehicleIdAndExitTimeIsNull(Long vehicleId);

    /** The open (still inside) visit for this plate, for members and visitors who gave a plate. */
    Optional<AccessLog> findFirstByIdentifierReadAndExitTimeIsNullOrderByEntryTimeDesc(String identifierRead);

    long countByAreaAndVehicleTypeAndExitTimeIsNull(ParkingArea area, VehicleType vehicleType);

    /** Vehicles still inside after their reservation ended (flagged by the background check). */
    long countByExitTimeIsNullAndOverstayTrue();

    List<AccessLog> findByExitTimeIsNullOrderByEntryTimeDesc();

    List<AccessLog> findAllByOrderByEntryTimeDesc(Pageable pageable);

    /** Still inside although the reservation ended before "cutoff" (end + grace has passed). */
    @Query("select l from AccessLog l where l.exitTime is null and l.reservation is not null "
            + "and l.overstay = false and l.reservation.endTime < :cutoff")
    List<AccessLog> findOverstaying(@Param("cutoff") LocalDateTime cutoff);

    /** Used by the cleanup job: finished visits older than the cutoff. */
    @Modifying
    @Query("delete from AccessLog l where l.exitTime is not null and l.entryTime < :cutoff")
    int deleteFinishedBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Visits that started in [from, to), with everything the export needs loaded in one query. */
    @Query("select l from AccessLog l left join fetch l.vehicle v left join fetch v.owner "
            + "left join fetch l.reservation where l.entryTime >= :from and l.entryTime < :to order by l.entryTime")
    List<AccessLog> findForExport(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query("select l from AccessLog l where l.vehicle.owner.id = :ownerId order by l.entryTime desc")
    List<AccessLog> findByOwner(@Param("ownerId") Long ownerId, Pageable pageable);
}
