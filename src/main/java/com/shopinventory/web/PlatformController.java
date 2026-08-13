package com.shopinventory.web;

import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.PlatformService;
import com.shopinventory.web.dto.Dtos.CreatePlatformUserRequest;
import com.shopinventory.web.dto.Dtos.OnboardOrgRequest;
import com.shopinventory.web.dto.Dtos.OnboardOrgResponse;
import com.shopinventory.web.dto.Dtos.OrgStatusRequest;
import com.shopinventory.web.dto.Dtos.PlatformAdminResponse;
import com.shopinventory.web.dto.Dtos.PlatformAuditResponse;
import com.shopinventory.web.dto.Dtos.PlatformOrgSummary;
import com.shopinventory.web.dto.Dtos.PlatformUserResponse;
import com.shopinventory.web.dto.Dtos.UpdateAdminRequest;
import com.shopinventory.web.dto.Dtos.UpdatePlatformUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_ONBOARD + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardOrgResponse onboard(@AuthenticationPrincipal AppPrincipal principal,
                                      @Valid @RequestBody OnboardOrgRequest request) {
        return platformService.onboardOrg(principal, request);
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAuthority('" + Capabilities.ORG_READ + "')")
    public List<PlatformOrgSummary> orgs(@AuthenticationPrincipal AppPrincipal principal) {
        return platformService.listOrgs();
    }

    @PatchMapping("/organizations/{orgId}/status")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_SUSPEND + "')")
    public void setOrgStatus(@AuthenticationPrincipal AppPrincipal principal,
                             @PathVariable UUID orgId,
                             @Valid @RequestBody OrgStatusRequest request) {
        platformService.setOrgStatus(principal, orgId, request.status());
    }

    @PatchMapping("/organizations/{orgId}/admin")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_ADMIN_RESET + "')")
    public PlatformAdminResponse updateAdmin(@AuthenticationPrincipal AppPrincipal principal,
                                             @PathVariable UUID orgId,
                                             @RequestBody UpdateAdminRequest request) {
        return platformService.updateOrgAdmin(principal, orgId, request);
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_TEAM_MANAGE + "')")
    public List<PlatformUserResponse> team(@AuthenticationPrincipal AppPrincipal principal) {
        return platformService.listPlatformUsers();
    }

    @PostMapping("/team")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_TEAM_MANAGE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformUserResponse createTeamMember(@AuthenticationPrincipal AppPrincipal principal,
                                                 @Valid @RequestBody CreatePlatformUserRequest request) {
        return platformService.createPlatformUser(principal, request);
    }

    @PatchMapping("/team/{userId}")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_TEAM_MANAGE + "')")
    public PlatformUserResponse updateTeamMember(@AuthenticationPrincipal AppPrincipal principal,
                                                 @PathVariable UUID userId,
                                                 @RequestBody UpdatePlatformUserRequest request) {
        return platformService.updatePlatformUser(principal, userId, request);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('" + Capabilities.PLATFORM_TEAM_MANAGE + "')")
    public List<PlatformAuditResponse> audit(@AuthenticationPrincipal AppPrincipal principal) {
        return platformService.listPlatformAudit();
    }
}
