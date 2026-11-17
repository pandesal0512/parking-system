package com.mabini.parking.log;

import com.mabini.parking.common.enums.VehicleType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    boolean existsByVehicleIdAndExitTimeIsNull(Long vehicleId);

    /** The open (still inside) visit for this plate, for members and visitors who gave a plate. */
    Optional<AccessLog> findFirstByIdentifierReadAndExitTimeIsNullOrderByEntryTimeDesc(String identifierRead);

    long countByVehicleTypeAndExitTimeIsNull(VehicleType vehicleType);

    List<AccessLog> findByExitTimeIsNullOrderByEntryTimeDesc();

    List<AccessLog> findAllByOrderByEntryTimeDesc(Pageable pageable);

    long countByEntryTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query("select l from AccessLog l where l.vehicle.owner.id = :ownerId order by l.entryTime desc")
    List<AccessLog> findByOwner(@Param("ownerId") Long ownerId, Pageable pageable);
}
