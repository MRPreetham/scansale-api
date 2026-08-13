package com.shopinventory.security;

import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.organization.OrgStatus;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.PlatformRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final OrganizationRepository organizationRepository;

    public JwtAuthFilter(JwtService jwtService, OrganizationRepository organizationRepository) {
        this.jwtService = jwtService;
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Claims claims = jwtService.parse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                String email = claims.get("email", String.class);
                String platformRoleName = claims.get("platform_role", String.class);

                if (platformRoleName != null) {
                    PlatformRole platformRole = PlatformRole.valueOf(platformRoleName);
                    AppPrincipal principal = new AppPrincipal(userId, email, null, null, null, platformRole);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authoritiesFor(
                                    platformRole.authority()));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    OrgRole role = OrgRole.valueOf(claims.get("role", String.class));
                    UUID orgId = UUID.fromString(claims.get("org_id", String.class));

                    // Enforce the org's CURRENT status (suspend/resume takes effect immediately,
                    // not when the JWT expires).
                    OrgStatus orgStatus = organizationRepository.findById(orgId)
                            .map(org -> org.getStatus())
                            .orElse(OrgStatus.SUSPENDED);

                    if (orgStatus != OrgStatus.SUSPENDED) {
                        AppPrincipal principal = new AppPrincipal(userId, email, orgId, role, orgStatus, null);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authoritiesFor(
                                        role.authority()));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> authoritiesFor(String roleAuthority) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(roleAuthority));
        for (String capability : Capabilities.forRoleAuthority(roleAuthority)) {
            authorities.add(new SimpleGrantedAuthority(capability));
        }
        return authorities;
    }
}