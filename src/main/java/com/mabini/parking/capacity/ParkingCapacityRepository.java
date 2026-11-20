package com.mabini.parking.capacity;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ParkingCapacityRepository extends JpaRepository<ParkingCapacity, Long> {

    Optional<ParkingCapacity> findByAreaAndVehicleType(ParkingArea area, VehicleType vehicleType);

    /**
     * Reads the capacity row AND locks it until the current transaction ends. Anything that
     * adds to the lot (reservations, visitors) takes this lock first, so two requests for the
     * last space can't both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ParkingCapacity c where c.area = :area and c.vehicleType = :type")
    Optional<ParkingCapacity> findForUpdate(@Param("area") ParkingArea area, @Param("type") VehicleType type);
}
