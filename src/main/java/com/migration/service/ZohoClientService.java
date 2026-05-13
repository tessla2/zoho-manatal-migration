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
                    properties.baseUrl() +
                            "/Candidates?page=1&per_page=10";

            log.info("Chamando URL: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Status code: {}", response.statusCode());
            log.info("Headers:");
            response.headers().map().forEach((key, values) ->
                log.info("  {}: {}", key, String.join(", ", values))
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Zoho API retornou status " + response.statusCode());
            }

            return response.body();
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Zoho: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar candidato no Zoho", e);
        }
    }
}
