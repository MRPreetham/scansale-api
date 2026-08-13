package com.shopinventory.domain.user;

public enum PlatformRole {
    SUPER_ADMIN,
    SUPPORT;

    public String authority() {
        return "ROLE_" + name();
    }
}
