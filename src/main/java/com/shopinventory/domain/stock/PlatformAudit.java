package com.shopinventory.domain.stock;

import com.shopinventory.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_audit",
        indexes = @Index(name = "idx_platform_audit_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PlatformAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 60)
    private String entityType;

    @Column(length = 60)
    private String entityId;

    @Column(columnDefinition = "text")
    private String detailsJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
