package com.shopinventory.service;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.organization.OrgStatus;
import com.shopinventory.domain.user.Membership;
import com.shopinventory.domain.user.MembershipRepository;
import com.shopinventory.domain.user.MembershipStatus;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.PlatformRole;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.web.ApiException;
import com.shopinventory.domain.stock.PlatformAuditRepository;
import com.shopinventory.web.dto.Dtos.CreatePlatformUserRequest;
import com.shopinventory.web.dto.Dtos.OnboardOrgRequest;
import com.shopinventory.web.dto.Dtos.OnboardOrgResponse;
import com.shopinventory.web.dto.Dtos.PlatformAdminResponse;
import com.shopinventory.web.dto.Dtos.PlatformAuditResponse;
import com.shopinventory.web.dto.Dtos.PlatformOrgSummary;
import com.shopinventory.web.dto.Dtos.PlatformUserResponse;
import com.shopinventory.web.dto.Dtos.UpdateAdminRequest;
import com.shopinventory.web.dto.Dtos.UpdatePlatformUserRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlatformService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PlatformAuditRepository platformAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PlatformService(OrganizationRepository organizationRepository,
                           UserRepository userRepository,
                           MembershipRepository membershipRepository,
                           PlatformAuditRepository platformAuditRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.platformAuditRepository = platformAuditRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public OnboardOrgResponse onboardOrg(AppPrincipal principal, OnboardOrgRequest request) {
        if (organizationRepository.existsByName(request.orgName().trim())) {
            throw ApiException.conflict("An organization with this name already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.adminEmail())) {
            throw ApiException.conflict("A user with this email already exists");
        }

        Organization org = new Organization();
        org.setName(request.orgName().trim());
        if (request.currency() != null && !request.currency().isBlank()) {
            org.setCurrency(request.currency().trim().toUpperCase());
        }
        Organization savedOrg = organizationRepository.save(org);

        User admin = new User();
        admin.setEmail(request.adminEmail().trim().toLowerCase());
        admin.setName(request.adminName().trim());
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        User savedAdmin = userRepository.save(admin);

        Membership membership = new Membership();
        membership.setOrg(savedOrg);
        membership.setUser(savedAdmin);
        membership.setRole(OrgRole.ADMIN);
        membership.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(membership);

        User actor = userRepository.findById(principal.userId()).orElseThrow();
        auditService.log(savedOrg.getId(), actor, "ORG_ONBOARDED", "Organization", savedOrg.getId().toString(),
                Map.of("orgName", savedOrg.getName(), "adminEmail", savedAdmin.getEmail()));
        auditService.logPlatform(actor, "ORG_ONBOARDED", "Organization", savedOrg.getId().toString(),
                Map.of("orgName", savedOrg.getName(), "adminEmail", savedAdmin.getEmail()));

        return new OnboardOrgResponse(savedOrg.getId(), savedOrg.getName(), savedOrg.getCurrency(),
                savedOrg.getStatus().name(), savedAdmin.getId(), savedAdmin.getEmail(), savedAdmin.getName());
    }

    @Transactional(readOnly = true)
    public List<PlatformOrgSummary> listOrgs() {
        List<Organization> orgs = organizationRepository.findAll(Sort.by("createdAt"));
        Map<UUID, List<Membership>> membershipsByOrg = membershipRepository
                .findByOrgIdInOrderByCreatedAtAsc(orgs.stream().map(Organization::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(m -> m.getOrg().getId()));
        return orgs.stream()
                .map(org -> {
                    List<Membership> memberships = membershipsByOrg.getOrDefault(org.getId(), List.of());
                    String adminEmail = memberships.stream()
                            .filter(m -> m.getRole() == OrgRole.ADMIN)
                            .findFirst()
                            .map(m -> m.getUser().getEmail())
                            .orElse(null);
                    return new PlatformOrgSummary(org.getId(), org.getName(), org.getStatus(),
                            org.getCurrency(), org.getCreatedAt(), adminEmail, memberships.size());
                })
                .toList();
    }

    @Transactional
    public void setOrgStatus(AppPrincipal principal, UUID orgId, OrgStatus status) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
        if (status == OrgStatus.SUSPENDED && membershipRepository.existsByOrgIdAndUserId(orgId, principal.userId())) {
            throw ApiException.badRequest("You cannot suspend an organization you belong to");
        }
        org.setStatus(status);
        organizationRepository.save(org);
        auditService.log(orgId, loadActor(principal), "ORG_STATUS_CHANGED", "Organization", orgId.toString(),
                Map.of("status", status.name()));
        auditService.logPlatform(loadActor(principal), "ORG_STATUS_CHANGED", "Organization", orgId.toString(),
                Map.of("orgName", org.getName(), "status", status.name()));
    }

    @Transactional
    public PlatformAdminResponse updateOrgAdmin(AppPrincipal principal, UUID orgId, UpdateAdminRequest request) {
        User admin = findAdminMembership(orgId)
                .orElseThrow(() -> ApiException.notFound("No admin account for this organization"))
                .getUser();
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim().toLowerCase();
            userRepository.findByEmailIgnoreCase(newEmail)
                    .filter(existing -> !existing.getId().equals(admin.getId()))
                    .ifPresent(existing -> {
                        throw ApiException.conflict("A user with this email already exists");
                    });
            admin.setEmail(newEmail);
        }
        if (request.name() != null && !request.name().isBlank()) {
            admin.setName(request.name().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            admin.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(admin);
        auditService.log(orgId, loadActor(principal), "ORG_ADMIN_UPDATED", "User", admin.getId().toString(),
                Map.of("email", admin.getEmail()));
        auditService.logPlatform(loadActor(principal), "ORG_ADMIN_UPDATED", "Organization", orgId.toString(),
                Map.of("email", admin.getEmail()));
        return new PlatformAdminResponse(admin.getId(), admin.getEmail(), admin.getName());
    }

    private Optional<Membership> findAdminMembership(UUID orgId) {
        return membershipRepository.findByOrgIdOrderByCreatedAtAsc(orgId).stream()
                .filter(m -> m.getRole() == OrgRole.ADMIN)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> listPlatformUsers() {
        return userRepository.findByPlatformRoleNotNull().stream()
                .map(u -> new PlatformUserResponse(u.getId(), u.getEmail(), u.getName(), u.getPlatformRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformAuditResponse> listPlatformAudit() {
        return platformAuditRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(a -> new PlatformAuditResponse(
                        a.getId(), a.getActor().getEmail(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getDetailsJson(), a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public PlatformUserResponse createPlatformUser(AppPrincipal principal, CreatePlatformUserRequest request) {
        if (request.platformRole() == null) {
            throw ApiException.badRequest("platformRole is required");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("A user with this email already exists");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPlatformRole(request.platformRole());
        User saved = userRepository.save(user);
        auditService.logPlatform(loadActor(principal), "PLATFORM_USER_CREATED", "User", saved.getId().toString(),
                Map.of("email", saved.getEmail(), "platformRole", saved.getPlatformRole().name()));
        return new PlatformUserResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getPlatformRole());
    }

    @Transactional
    public PlatformUserResponse updatePlatformUser(AppPrincipal principal, UUID userId, UpdatePlatformUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getPlatformRole() == null) {
            throw ApiException.badRequest("User is not a platform user");
        }
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim().toLowerCase();
            userRepository.findByEmailIgnoreCase(newEmail)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw ApiException.conflict("A user with this email already exists");
                    });
            user.setEmail(newEmail);
        }
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.platformRole() != null && request.platformRole() != user.getPlatformRole()) {
            if (user.getPlatformRole() == PlatformRole.SUPER_ADMIN) {
                throw ApiException.badRequest("The Super Admin role cannot be changed");
            }
            if (user.getId().equals(principal.userId())) {
                throw ApiException.badRequest("You cannot change your own platform role");
            }
            user.setPlatformRole(request.platformRole());
        }
        User saved = userRepository.save(user);
        auditService.logPlatform(loadActor(principal), "PLATFORM_USER_UPDATED", "User", saved.getId().toString(),
                Map.of("email", saved.getEmail(), "platformRole", saved.getPlatformRole().name()));
        return new PlatformUserResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getPlatformRole());
    }

    private User loadActor(AppPrincipal principal) {
        return userRepository.findById(principal.userId()).orElseThrow();
    }
}
