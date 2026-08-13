package com.shopinventory.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockImportRepository extends JpaRepository<StockImport, UUID> {

    Optional<StockImport> findByOrgIdAndId(UUID orgId, UUID id);

    org.springframework.data.domain.Page<StockImport> findByOrgIdOrderByImportedAtDesc(
            UUID orgId, org.springframework.data.domain.Pageable pageable);
}