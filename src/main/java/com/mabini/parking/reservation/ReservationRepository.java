package com.mabini.parking.reservation;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservations of one area and vehicle type with the given status that overlap [start, end). */
    @Query("select r from Reservation r where r.area = :area and r.vehicleType = :type and r.status = :status "
            + "and r.startTime < :windowEnd and r.endTime > :windowStart")
    List<Reservation> findOverlapping(@Param("area") ParkingArea area,
                                      @Param("type") VehicleType type,
                                      @Param("status") ReservationStatus status,
                                      @Param("windowStart") LocalDateTime start,
                                      @Param("windowEnd") LocalDateTime end);

    /** How many reservations this user already has that overlap [start, end). */
    @Query("select count(r) from Reservation r where r.user.id = :userId and r.status in :statuses "
            + "and r.startTime < :windowEnd and r.endTime > :windowStart")
    long countOverlappingForUser(@Param("userId") Long userId,
                                 @Param("statuses") Collection<ReservationStatus> statuses,
                                 @Param("windowStart") LocalDateTime start,
                                 @Param("windowEnd") LocalDateTime end);

    /**
     * Reservations a vehicle may use to enter right now: it must not have ended, and it must
     * start no later than "latestStart" (now plus the early-entry grace period).
     */
    /** Every reservation that could let its vehicle in right now (same time rule as findUsable), vehicle and owner included. */
    @Query("select r from Reservation r join fetch r.vehicle v join fetch v.owner where r.status = :status "
            + "and r.startTime <= :latestStart and r.endTime > :now order by r.startTime")
    List<Reservation> findAllUsableNow(@Param("status") ReservationStatus status,
                                       @Param("latestStart") LocalDateTime latestStart,
                                       @Param("now") LocalDateTime now);

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

    /** Still ACTIVE after they ended, and the vehicle never entered: these are the no-shows. */
    @Query("select r from Reservation r where r.status = :status and r.endTime <= :now "
            + "and not exists (select 1 from AccessLog l where l.reservation.id = r.id)")
    List<Reservation> findUnusedEnded(@Param("status") ReservationStatus status,
                                      @Param("now") LocalDateTime now);

    /** Reservations this user made that start inside [from, to) and still count toward the daily limit. */
    @Query("select count(r) from Reservation r where r.user.id = :userId and r.status in :statuses "
            + "and r.startTime >= :from and r.startTime < :to")
    long countForUserStartingBetween(@Param("userId") Long userId,
                                     @Param("statuses") Collection<ReservationStatus> statuses,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    List<Reservation> findByStatusOrderByStartTimeAsc(ReservationStatus status);

    List<Reservation> findByStatusAndEndTimeLessThanEqual(ReservationStatus status, LocalDateTime time);

    List<Reservation> findByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

    List<Reservation> findByVehicleIdAndStatusIn(Long vehicleId, Collection<ReservationStatus> statuses);

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<ReservationStatus> statuses);

    long countByStatus(ReservationStatus status);

    List<Reservation> findByUserIdOrderByStartTimeDesc(Long userId);

    List<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status);

    List<Reservation> findByVehicleIdAndStatus(Long vehicleId, ReservationStatus status);

    boolean existsByVehicleIdAndStatus(Long vehicleId, ReservationStatus status);

    long countByStatusAndStartTimeBetween(ReservationStatus status, LocalDateTime from, LocalDateTime to);
}
