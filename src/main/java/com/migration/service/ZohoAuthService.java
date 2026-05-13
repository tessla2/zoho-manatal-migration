package com.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
public class ZohoAuthService {

    private final ZohoProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateAccessToken() throws Exception {
        String body =
                "refresh_token=" + properties.refreshToken() +
                        "&client_id=" + properties.clientId() +
                        "&client_secret=" + properties.clientSecret() +
                        "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.accountsUrl() + "/com/migration/oauth/v2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode json = mapper.readTree(response.body());

        return json.get("access_token").asText();
    }
}
