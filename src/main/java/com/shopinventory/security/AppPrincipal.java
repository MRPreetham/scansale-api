package com.shopinventory.security;

import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.PlatformRole;
import com.shopinventory.domain.organization.OrgStatus;

import java.util.UUID;

public record AppPrincipal(
        UUID userId,
        String email,
        UUID orgId,
        OrgRole role,
        OrgStatus orgStatus,
        PlatformRole platformRole) {
}
