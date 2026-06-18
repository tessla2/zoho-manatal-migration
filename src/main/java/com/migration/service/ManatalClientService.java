package com.migration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.exception.ApiException;
import com.migration.model.ManatalAttachment;
import com.migration.model.ManatalCandidate;
import com.migration.model.ManatalResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManatalClientService {

    @Value("${migration.manatal.base-url}")
    private String baseUrl;

    @Value("${migration.manatal.token}")
    private String token;

    @Value("${migration.manatal.rate-limit-ms:600}")
    private long rateLimitMs;

    private final AtomicLong lastCallTime = new AtomicLong(0);

    private final ObjectMapper mapper = new ObjectMapper();

    private void throttle() {
        long now = System.currentTimeMillis();
        long last = lastCallTime.get();
        long elapsed = now - last;
        if (elapsed < rateLimitMs) {
            try {
                Thread.sleep(rateLimitMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallTime.set(System.currentTimeMillis());
    }

    private String normalizedBaseUrl() {
        return baseUrl.replaceAll("/+$", "");
    }

    public String fetchCandidateById(String candidateId) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/";

        log.info("Fetching candidate {} from Manatal: {}", candidateId, url);

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
                log.error("Erro ao buscar candidato {} no Manatal. Status: {}, Body: {}", candidateId, response.statusCode(), response.body());
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

    public String fetchOneCandidate() {
        throttle();
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


    public Map<String, Object> fetchCustomFieldsByCandidateId(String candidateId) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/";

        log.info("Fetching candidate data from Manatal: {}", candidateId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao buscar candidato {} no Manatal. Status: {}, Body: {}", candidateId, response.statusCode(), response.body());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            JsonNode root = mapper.readTree(response.body());

            // Retorna o response completo para inspecionar todos os campos
            return mapper.convertValue(root, new TypeReference<Map<String, Object>>() {});

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar campos do candidato {} no Manatal: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public Map<String, Object> fetchFirstCandidateCustomFields() {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/?limit=1";

        log.info("Fetching first candidate custom_fields from Manatal: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao listar candidatos no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.size() == 0) {
                log.warn("Nenhum candidato encontrado no Manatal para verificar custom_fields");
                return Map.of();
            }

            JsonNode customFields = results.get(0).get("custom_fields");
            if (customFields == null || customFields.isNull()) {
                log.warn("Primeiro candidato nao possui custom_fields em Manatal");
                return Map.of();
            }

            return mapper.convertValue(customFields, new TypeReference<Map<String, Object>>() {});

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar custom_fields do primeiro candidato no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public String createAttachment(String candidateId, ManatalAttachment attachment) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/attachments/";

        log.info("POSTing attachment to Manatal: {}", url);

        try {
            String json = mapper.writeValueAsString(attachment);
            log.debug("Attachment payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal attachment response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao criar attachment no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Manatal API retornou status " + response.statusCode());
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar attachment no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public String updateResume(String candidateId, ManatalResume resume) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/resume/";

        log.info("POSTing resume to Manatal: {}", url);

        try {
            String json = mapper.writeValueAsString(resume);
            log.debug("Resume payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal resume response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao atualizar resume no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Manatal API retornou status " + response.statusCode());
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar resume no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public void createNote(String candidateId, String info) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/" + candidateId + "/notes/";

        log.info("POSTing note to Manatal: {}", url);

        try {
            String json = mapper.writeValueAsString(java.util.Map.of("info", info));
            log.debug("Note payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal note response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao criar nota no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Manatal API retornou status " + response.statusCode());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar nota no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public void createSocialMedia(String candidateId, String socialMedia, String url) {
        throttle();
        String endpoint = normalizedBaseUrl() + "/candidates/" + candidateId + "/social-media/";

        log.info("POSTing social-media to Manatal: {}", endpoint);

        try {
            String json = mapper.writeValueAsString(Map.of(
                    "social_media", socialMedia,
                    "social_media_url", url
            ));
            log.debug("Social-media payload: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal social-media response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao criar social-media no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Manatal API retornou status " + response.statusCode());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao criar social-media no Manatal: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Manatal");
        }
    }

    public String createCandidate(ManatalCandidate candidate) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/";

        log.info("POSTing candidate to Manatal: {}", url);

        try {
            String json = mapper.writeValueAsString(candidate);
            log.info("POST payload para Manatal: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Token " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Manatal response status: {}", response.statusCode());
            log.info("Manatal response body: {}", response.body());

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
