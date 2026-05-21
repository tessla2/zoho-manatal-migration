package com.migration.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "migration.manatal")
public record ManatalProperties(
        OAuth oauth,
        String baseUrl
) {

    public record OAuth(
            String tokenUrl
    ) {
    }
}
