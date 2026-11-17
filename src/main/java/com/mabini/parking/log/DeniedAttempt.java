package com.mabini.parking.log;

import com.mabini.parking.common.enums.DenyReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Every time the gate refused a vehicle. */
@Entity
@Table(name = "denied_attempts")
public class DeniedAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30)
    private String identifierRead;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private DenyReason reason;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    private Long recordedBy;

    public Long getId() {
        return id;
    }

    public String getIdentifierRead() {
        return identifierRead;
    }

    public void setIdentifierRead(String identifierRead) {
        this.identifierRead = identifierRead;
    }

    public DenyReason getReason() {
        return reason;
    }

    public void setReason(DenyReason reason) {
        this.reason = reason;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(LocalDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
    }
}
