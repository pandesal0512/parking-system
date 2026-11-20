package com.mabini.parking.vehicle;

import com.mabini.parking.common.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByOwnerId(Long ownerId);

    Optional<Vehicle> findByIdentifier(String identifier);

    boolean existsByIdentifier(String identifier);

    List<Vehicle> findAllByOrderByCreatedAtDesc();

    List<Vehicle> findByStatusOrderByCreatedAtDesc(VehicleStatus status);

    long countByStatus(VehicleStatus status);
}
