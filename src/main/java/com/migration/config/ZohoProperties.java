package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "migration.zoho")
public record ZohoProperties(

        OAuth oauth,
        String baseUrl,
        String tagName

) {

    public record OAuth(

            String clientId,
            String clientSecret,
            String refreshToken,
            String tokenUrl

    ) {
    }
}