package com.shopinventory.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlatformAuditRepository extends JpaRepository<PlatformAudit, UUID> {

    List<PlatformAudit> findTop50ByOrderByCreatedAtDesc();
}
