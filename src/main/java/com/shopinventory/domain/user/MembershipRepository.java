package com.shopinventory.domain.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<Membership> findByOrgIdOrderByCreatedAtAsc(UUID orgId);

    @EntityGraph(attributePaths = "user")
    List<Membership> findByOrgIdInOrderByCreatedAtAsc(java.util.Collection<UUID> orgIds);

    Optional<Membership> findByOrgIdAndUserId(UUID orgId, UUID userId);

    boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);

    long countByOrgId(UUID orgId);
}