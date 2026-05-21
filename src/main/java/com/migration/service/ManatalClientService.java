package com.migration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.exception.ApiException;
import com.migration.model.ManatalCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    private String normalizedBaseUrl() {
        return baseUrl.replaceAll("/+$", "");
    }

    public String fetchOneCandidate() {
        String url = normalizedBaseUrl() + "/candidates/?limit=1";

        log.info("Fetching one candidate from Manatal: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao buscar candidato no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }


    public String fetchCandidateActivities(String candidateId) {
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/activities/";

        log.info("Fetching activities for candidate {} from Manatal: {}", candidateId, url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao buscar atividades do candidato {} no Manatal. Status: {}, Body: {}",
                        candidateId, response.statusCode(), response.body());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar atividades do candidato {} no Manatal: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public String createCandidate(ManatalCandidate candidate) {
        String url = normalizedBaseUrl() + "/candidates/";

        log.info("POSTing candidate to Manatal: {}", url);

        try {
            String json = mapper.writeValueAsString(candidate);
            log.debug("Payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao criar candidato no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar candidato no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }
}
