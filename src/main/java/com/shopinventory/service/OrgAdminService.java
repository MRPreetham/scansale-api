package com.shopinventory.service;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.user.Membership;
import com.shopinventory.domain.user.MembershipRepository;
import com.shopinventory.domain.user.MembershipStatus;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.OrgUserRequest;
import com.shopinventory.web.dto.Dtos.OrgUserResponse;
import com.shopinventory.web.dto.Dtos.SettingsRequest;
import com.shopinventory.web.dto.Dtos.SettingsResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrgAdminService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public OrgAdminService(OrganizationRepository organizationRepository,
                           MembershipRepository membershipRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SettingsResponse settings(UUID orgId) {
        Organization org = loadOrg(orgId);
        return new SettingsResponse(org.getName(), org.getCurrency());
    }

    @Transactional
    public SettingsResponse updateSettings(UUID orgId, AppPrincipal principal, SettingsRequest request) {
        Organization org = loadOrg(orgId);
        if (request.orgName() != null && !request.orgName().isBlank()) {
            org.setName(request.orgName().trim());
        }
        if (request.currency() != null && !request.currency().isBlank()) {
            org.setCurrency(request.currency().trim().toUpperCase());
        }
        organizationRepository.save(org);
        auditService.log(orgId, loadActor(principal), "ORG_SETTINGS_UPDATE", "Organization", orgId.toString(),
                Map.of("orgName", org.getName(), "currency", org.getCurrency()));
        return new SettingsResponse(org.getName(), org.getCurrency());
    }

    @Transactional(readOnly = true)
    public List<OrgUserResponse> listUsers(UUID orgId) {
        return membershipRepository.findByOrgIdOrderByCreatedAtAsc(orgId).stream()
                .map(m -> new OrgUserResponse(m.getUser().getId(), m.getUser().getEmail(),
                        m.getUser().getName(), m.getRole(), m.getStatus()))
                .toList();
    }

    @Transactional
    public OrgUserResponse addUser(UUID orgId, AppPrincipal principal, OrgUserRequest request) {
        requireCanAssign(principal, request.role());

        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(request.email());
            user.setName(request.name());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user = userRepository.save(user);
        }

        if (membershipRepository.existsByOrgIdAndUserId(orgId, user.getId())) {
            throw ApiException.conflict("User is already a member of this organization");
        }

        Membership membership = new Membership();
        Organization orgRef = new Organization();
        orgRef.setId(orgId);
        membership.setOrg(orgRef);
        membership.setUser(user);
        membership.setRole(request.role());
        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        auditService.log(orgId, loadActor(principal), "USER_ADDED", "Membership", membership.getId().toString(),
                Map.of("email", user.getEmail(), "role", request.role().name()));
        return new OrgUserResponse(user.getId(), user.getEmail(), user.getName(), membership.getRole(), membership.getStatus());
    }

    @Transactional
    public OrgUserResponse changeRole(UUID orgId, AppPrincipal principal, UUID userId, OrgRole newRole) {
        membershipOrThrow(orgId, userId);
        if (principal.userId().equals(userId)) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        requireCanAssign(principal, newRole);
        Membership membership = membershipRepository.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> ApiException.notFound("Membership not found"));
        membership.setRole(newRole);
        membershipRepository.save(membership);
        auditService.log(orgId, loadActor(principal), "USER_ROLE_CHANGED", "Membership", membership.getId().toString(),
                Map.of("userId", userId.toString(), "role", newRole.name()));
        return new OrgUserResponse(membership.getUser().getId(), membership.getUser().getEmail(),
                membership.getUser().getName(), membership.getRole(), membership.getStatus());
    }

    @Transactional
    public void removeUser(UUID orgId, AppPrincipal principal, UUID userId) {
        if (principal.userId().equals(userId)) {
            throw ApiException.badRequest("You cannot remove your own account");
        }
        Membership membership = membershipRepository.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> ApiException.notFound("Membership not found"));
        membershipRepository.delete(membership);
        auditService.log(orgId, loadActor(principal), "USER_REMOVED", "Membership", membership.getId().toString(),
                Map.of("userId", userId.toString()));
    }

    private void requireCanAssign(AppPrincipal principal, OrgRole targetRole) {
        if (principal.role() == OrgRole.ADMIN
                && (targetRole == OrgRole.ADMIN || targetRole == OrgRole.SALES || targetRole == OrgRole.INVENTORY)) {
            return;
        }
        throw ApiException.forbidden("You are not allowed to assign the " + targetRole + " role");
    }

    private void membershipOrThrow(UUID orgId, UUID userId) {
        if (!membershipRepository.existsByOrgIdAndUserId(orgId, userId)) {
            throw ApiException.notFound("User is not a member of this organization");
        }
    }

    private Organization loadOrg(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.forbidden("Organization not found"));
    }

    private User loadActor(AppPrincipal principal) {
        return userRepository.findById(principal.userId()).orElseThrow();
    }
}