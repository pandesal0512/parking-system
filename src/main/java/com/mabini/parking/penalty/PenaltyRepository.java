package com.mabini.parking.penalty;

import com.mabini.parking.common.enums.PenaltyType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    boolean existsByReservationIdAndType(Long reservationId, PenaltyType type);

    List<Penalty> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Penalty> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
