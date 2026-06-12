package com.migration.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "migration.security")
public record SecurityProperties(
        String jwtSecret,
        long jwtExpirationHours,
        String adminUsername,
        String adminPassword
) {
}
