package com.shopinventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String adminEmail,
        String adminPassword,
        String superAdminEmail,
        String superAdminPassword,
        String firstOrgName,
        String defaultCurrency,
        Jwt jwt) {

    public record Jwt(String secret, long expirationMs) {
    }
}