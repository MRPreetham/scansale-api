package com.shopinventory.web;

import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.security.AppPrincipal;
import com.shopinventory.security.Capabilities;
import com.shopinventory.service.OrgAdminService;
import com.shopinventory.web.dto.Dtos.OrgUserRequest;
import com.shopinventory.web.dto.Dtos.OrgUserResponse;
import com.shopinventory.web.dto.Dtos.SettingsRequest;
import com.shopinventory.web.dto.Dtos.SettingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
public class OrgAdminController {

    private final OrgAdminService orgAdminService;

    public OrgAdminController(OrgAdminService orgAdminService) {
        this.orgAdminService = orgAdminService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('" + Capabilities.SETTINGS_MANAGE + "')")
    public SettingsResponse settings(@AuthenticationPrincipal AppPrincipal principal) {
        return orgAdminService.settings(principal.orgId());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('" + Capabilities.SETTINGS_MANAGE + "')")
    public SettingsResponse updateSettings(@AuthenticationPrincipal AppPrincipal principal,
                                           @Valid @RequestBody SettingsRequest request) {
        return orgAdminService.updateSettings(principal.orgId(), principal, request);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + Capabilities.USER_MANAGE + "')")
    public List<OrgUserResponse> users(@AuthenticationPrincipal AppPrincipal principal) {
        return orgAdminService.listUsers(principal.orgId());
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('" + Capabilities.USER_MANAGE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgUserResponse addUser(@AuthenticationPrincipal AppPrincipal principal,
                                   @Valid @RequestBody OrgUserRequest request) {
        return orgAdminService.addUser(principal.orgId(), principal, request);
    }

    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasAuthority('" + Capabilities.USER_MANAGE + "')")
    public OrgUserResponse changeRole(@AuthenticationPrincipal AppPrincipal principal,
                                      @PathVariable UUID userId,
                                      @RequestBody RoleRequest request) {
        return orgAdminService.changeRole(principal.orgId(), principal, userId, request.role());
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('" + Capabilities.USER_MANAGE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUser(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID userId) {
        orgAdminService.removeUser(principal.orgId(), principal, userId);
    }

    public record RoleRequest(OrgRole role) {
    }
}