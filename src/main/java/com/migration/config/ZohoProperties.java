package com.migration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "migration.zoho")
public record ZohoProperties(

        OAuth oauth,
        String baseUrl,
        String tagName,
        String successTagName,
        String dateStart,
        String dateEnd,
        int pageSize

) {

    public record OAuth(

            String clientId,
            String clientSecret,
            String refreshToken,
            String tokenUrl

    ) {
    }
}