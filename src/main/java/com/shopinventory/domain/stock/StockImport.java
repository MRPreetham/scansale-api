package com.shopinventory.domain.stock;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_imports")
@Getter
@Setter
@NoArgsConstructor
public class StockImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imported_by", nullable = false)
    private User importedBy;

    @Column(nullable = false, length = 200)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.PREVIEW;

    @Column(nullable = false)
    private int newCount;

    @Column(nullable = false)
    private int updateCount;

    @Column(nullable = false)
    private int skipCount;

    @Column(columnDefinition = "text")
    private String summaryJson;

    @Column(nullable = false, updatable = false)
    private Instant importedAt = Instant.now();
}