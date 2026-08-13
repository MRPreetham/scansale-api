package com.shopinventory.service;

import com.shopinventory.domain.user.Membership;
import com.shopinventory.domain.user.MembershipRepository;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.JwtService;
import com.shopinventory.web.ApiException;
import com.shopinventory.web.dto.Dtos.AuthResponse;
import com.shopinventory.web.dto.Dtos.LoginRequest;
import com.shopinventory.web.dto.Dtos.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, MembershipRepository membershipRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("Account is deactivated");
        }

        // Platform users (super admin / support) log in without any org membership.
        if (user.getPlatformRole() != null) {
            String token = jwtService.generatePlatform(user.getId(), user.getEmail(), user.getPlatformRole());
            return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(),
                    null, null, null, null, user.getPlatformRole(), null);
        }

        Membership membership = membershipRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getStatus() == com.shopinventory.domain.user.MembershipStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("No active organization assigned to this account"));

        com.shopinventory.domain.organization.Organization org = membership.getOrg();
        OrgRole role = membership.getRole();
        String token = jwtService.generateOrg(user.getId(), user.getEmail(), org.getId(), role,
                org.getStatus());

        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(),
                org.getId(), org.getName(), role, org.getStatus().name(), null, org.getCurrency());
    }

    @Transactional(readOnly = true)
    public MeResponse me(AppPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.forbidden("No active user"));
        if (principal.platformRole() != null) {
            return new MeResponse(user.getId(), user.getEmail(), user.getName(),
                    null, null, null, null, principal.platformRole(), null);
        }
        Membership membership = membershipRepository
                .findByOrgIdAndUserId(principal.orgId(), principal.userId())
                .orElseThrow(() -> ApiException.forbidden("No active organization"));
        com.shopinventory.domain.organization.Organization org = membership.getOrg();
        return new MeResponse(user.getId(), user.getEmail(), user.getName(),
                org.getId(), org.getName(), membership.getRole(), org.getStatus().name(), null, org.getCurrency());
    }
}