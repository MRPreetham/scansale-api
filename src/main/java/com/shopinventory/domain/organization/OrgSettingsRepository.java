package com.shopinventory.domain.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrgSettingsRepository extends JpaRepository<OrgSettings, UUID> {

    Optional<OrgSettings> findByOrgId(UUID orgId);
}
