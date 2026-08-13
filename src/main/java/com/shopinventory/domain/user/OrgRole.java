package com.shopinventory.domain.user;

public enum OrgRole {
    ADMIN,
    SALES,
    INVENTORY;

    public String authority() {
        return "ROLE_" + name();
    }
}