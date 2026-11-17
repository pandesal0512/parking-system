package com.mabini.parking.log;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DeniedAttemptRepository extends JpaRepository<DeniedAttempt, Long> {

    List<DeniedAttempt> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    long countByAttemptedAtBetween(LocalDateTime from, LocalDateTime to);
}
