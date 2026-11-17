package com.mabini.parking.reservation;

import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservations of one vehicle type with the given status that overlap [start, end). */
    @Query("select r from Reservation r where r.vehicleType = :type and r.status = :status "
            + "and r.startTime < :windowEnd and r.endTime > :windowStart")
    List<Reservation> findOverlapping(@Param("type") VehicleType type,
                                      @Param("status") ReservationStatus status,
                                      @Param("windowStart") LocalDateTime start,
                                      @Param("windowEnd") LocalDateTime end);

    /** How many reservations this user already has that overlap [start, end). */
    @Query("select count(r) from Reservation r where r.user.id = :userId and r.status = :status "
            + "and r.startTime < :windowEnd and r.endTime > :windowStart")
    long countOverlappingForUser(@Param("userId") Long userId,
                                 @Param("status") ReservationStatus status,
                                 @Param("windowStart") LocalDateTime start,
                                 @Param("windowEnd") LocalDateTime end);

    /**
     * Reservations a vehicle may use to enter right now: it must not have ended, and it must
     * start no later than "latestStart" (now plus the early-entry grace period).
     */
    @Query("select r from Reservation r where r.vehicle.id = :vehicleId and r.status = :status "
            + "and r.startTime <= :latestStart and r.endTime > :now order by r.startTime")
    List<Reservation> findUsable(@Param("vehicleId") Long vehicleId,
                                 @Param("status") ReservationStatus status,
                                 @Param("latestStart") LocalDateTime latestStart,
                                 @Param("now") LocalDateTime now);

    @Query("select r from Reservation r where r.startTime < :rangeEnd and r.endTime > :rangeStart order by r.startTime")
    List<Reservation> findInWindow(@Param("rangeStart") LocalDateTime from, @Param("rangeEnd") LocalDateTime to);

    @Query("select r from Reservation r where r.status = :status and r.startTime < :rangeEnd "
            + "and r.endTime > :rangeStart order by r.startTime")
    List<Reservation> findInWindowByStatus(@Param("rangeStart") LocalDateTime from,
                                           @Param("rangeEnd") LocalDateTime to,
                                           @Param("status") ReservationStatus status);

    List<Reservation> findByUserIdOrderByStartTimeDesc(Long userId);

    List<Reservation> findByVehicleIdAndStatus(Long vehicleId, ReservationStatus status);

    boolean existsByVehicleIdAndStatus(Long vehicleId, ReservationStatus status);

    long countByStatusAndStartTimeBetween(ReservationStatus status, LocalDateTime from, LocalDateTime to);
}
