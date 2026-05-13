package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "migration.zoho")
public record ZohoProperties(
        String clientId,
        String clientSecret,
        String refreshToken,
        String accountsUrl,
        String apiUrl
) {}
