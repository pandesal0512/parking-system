package com.mabini.parking.log;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeniedAttemptRepository extends JpaRepository<DeniedAttempt, Long> {

    List<DeniedAttempt> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    long countByAttemptedAtBetween(LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("delete from DeniedAttempt d where d.attemptedAt < :cutoff")
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
