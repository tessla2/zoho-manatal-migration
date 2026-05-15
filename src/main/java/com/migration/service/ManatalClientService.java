package com.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManatalClientService {

    @Value("${migration.manatal.base-url}")
    private String baseUrl;

    @Value("${migration.manatal.token}")
    private String token;

    private final ObjectMapper mapper = new ObjectMapper();

    public String createCandidate(ManatalCandidate candidate) {
        try {
            String json = mapper.writeValueAsString(candidate);
            String url = baseUrl + "/candidates";

            log.info("POSTing candidate to Manatal: {}", url);
            log.debug("Payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao criar candidato no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Manatal API retornou status " + response.statusCode());
            }

            return response.body();
        } catch (Exception e) {
            log.error("Erro ao criar candidato no Manatal: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar candidato no Manatal", e);
        }
    }
}
