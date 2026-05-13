package com.migration.service;

import com.migration.config.ZohoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoClientService {

    private final ZohoAuthService authService;
    private final ZohoProperties properties;


    public String fetchOneCandidate() {

        try {
            String token = authService.generateAccessToken();

            String url =
                    properties.apiUrl() +
                            "/recruit/v2/Candidates?page=1&per_page=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Zoho: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar candidato no Zoho", e);
        }
    }
}
