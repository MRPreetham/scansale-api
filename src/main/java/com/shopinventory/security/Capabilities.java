package com.shopinventory.security;

import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the permission matrix: which role authority
 * grants which capabilities. Adding a role = one entry here (plus a case in
 * JwtAuthFilter). Controllers reference the capability constants, never role
 * names directly.
 */
public final class Capabilities {

    private Capabilities() {
    }

    // Org-level capabilities
    public static final String PRODUCT_WRITE = "PRODUCT_WRITE";
    public static final String PRODUCT_DELETE = "PRODUCT_DELETE";
    public static final String STOCK_ADJUST = "STOCK_ADJUST";
    public static final String SALE_CREATE = "SALE_CREATE";
    public static final String IMPORT_EXECUTE = "IMPORT_EXECUTE";
    public static final String REPORT_READ = "REPORT_READ";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String USER_MANAGE = "USER_MANAGE";

    // Platform-level capabilities
    public static final String ORG_READ = "ORG_READ";
    public static final String PLATFORM_ONBOARD = "PLATFORM_ONBOARD";
    public static final String PLATFORM_SUSPEND = "PLATFORM_SUSPEND";
    public static final String PLATFORM_ADMIN_RESET = "PLATFORM_ADMIN_RESET";
    public static final String PLATFORM_TEAM_MANAGE = "PLATFORM_TEAM_MANAGE";

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "ROLE_ADMIN", Set.of(PRODUCT_WRITE, PRODUCT_DELETE, STOCK_ADJUST, SALE_CREATE,
                    IMPORT_EXECUTE, REPORT_READ, SETTINGS_MANAGE, USER_MANAGE),
            "ROLE_SALES", Set.of(SALE_CREATE),
            "ROLE_INVENTORY", Set.of(PRODUCT_WRITE, STOCK_ADJUST, IMPORT_EXECUTE, REPORT_READ),
            "ROLE_SUPER_ADMIN", Set.of(ORG_READ, PLATFORM_ONBOARD, PLATFORM_SUSPEND,
                    PLATFORM_ADMIN_RESET, PLATFORM_TEAM_MANAGE),
            "ROLE_SUPPORT", Set.of(ORG_READ, PLATFORM_ADMIN_RESET));

    public static Set<String> forRoleAuthority(String roleAuthority) {
        return BY_ROLE.getOrDefault(roleAuthority, Set.of());
    }
}
