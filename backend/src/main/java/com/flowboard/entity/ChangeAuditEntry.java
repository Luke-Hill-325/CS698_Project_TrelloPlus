package com.flowboard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "change_audit_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeAuditEntry {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_id", nullable = false)
    private Change change;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AuditAction {
        VIEWED,
        APPROVAL_REQUEST_CREATED,
        APPROVED,
        REJECTED,
        DEFERRED,
        APPLICATION_STARTED,
        APPLICATION_SUCCEEDED,
        APPLICATION_FAILED,
        ROLLED_BACK
    }
}
