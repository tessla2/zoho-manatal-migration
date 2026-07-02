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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManatalClientService {

    private static final int MAX_RETRIES = 4;
    private static final Pattern WAIT_SECONDS_PATTERN = Pattern.compile("available in (\\d+) second");

    private final HttpClient client;

    @Value("${migration.manatal.base-url}")
    private String baseUrl;

    @Value("${migration.manatal.token}")
    private String token;

    @Value("${migration.manatal.rate-limit-ms:900}")
    private long rateLimitMs;

    private final AtomicLong lastCallTime = new AtomicLong(0);

    private final ObjectMapper mapper = new ObjectMapper();

    private void throttle() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            long nextAllowed = lastCallTime.get();
            if (now < nextAllowed) {
                long sleepMs = nextAllowed - now;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                now = System.currentTimeMillis();
            }
            lastCallTime.set(now + rateLimitMs);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 429) {
                return response;
            }

            attempt++;
            if (attempt > MAX_RETRIES) {
                log.error("Excedeu {} tentativas após 429 consecutivos em {}. Desistindo.",
                        MAX_RETRIES, request.uri());
                return response;
            }

            long waitMs = extractWaitSeconds(response.body())
                    .map(seconds -> seconds * 1000L)
                    .orElse((long) attempt * 2000L)
                    + 500; // margem de segurança sobre o valor indicado pelo Manatal

            log.warn("429 recebido (tentativa {}/{}) em {}. Aguardando {}ms antes de repetir.",
                    attempt, MAX_RETRIES, request.uri(), waitMs);

            Thread.sleep(waitMs);
        }
    }

    private Optional<Long> extractWaitSeconds(String body) {
        if (body == null) {
            return Optional.empty();
        }
        Matcher m = WAIT_SECONDS_PATTERN.matcher(body);
        return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
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

        try {
            HttpResponse<String> response = sendWithRetry(request);

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

    public String searchCandidateByEmail(String email) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/?search=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);

        log.info("Searching candidate by email: {}", email);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = sendWithRetry(request);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Search by email returned status {} for {}", response.statusCode(), email);
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode candidate : results) {
                    String candidateEmail = candidate.path("email").asText("");
                    if (email.equalsIgnoreCase(candidateEmail)) {
                        String id = candidate.path("id").asText();
                        log.info("Found existing candidate in Manatal with id {} for email {}", id, email);
                        return id;
                    }
                }
                log.warn("Search returned candidates but none with exact email match for {}", email);
            }

            return null;
        } catch (Exception e) {
            log.warn("Error searching candidate by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    public String listCandidates(int limit, int offset) {
        throttle();
        String url = normalizedBaseUrl() + "/candidates/?limit=" + limit + "&offset=" + offset;

        log.debug("Listing candidates from Manatal: limit={} offset={}", limit, offset);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = sendWithRetry(request);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("List candidates returned status {} for limit={} offset={}", response.statusCode(), limit, offset);
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Manatal API retornou status " + response.statusCode()
                );
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar candidatos no Manatal: {}", e.getMessage(), e);
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

        try {
            HttpResponse<String> response = sendWithRetry(request);

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

        try {
            HttpResponse<String> response = sendWithRetry(request);

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



        try {
            HttpResponse<String> response = sendWithRetry(request);

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


            HttpResponse<String> response = sendWithRetry(request);

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


            HttpResponse<String> response = sendWithRetry(request);

            log.info("Manatal resume response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 400 && response.body().contains("already has a resume")) {
                    log.warn("Candidate {} already has a resume in Manatal, skipping", candidateId);
                    return "{\"status\":\"skipped\"}";
                }
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


            HttpResponse<String> response = sendWithRetry(request);

            log.info("Manatal note response status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 429) {
                    log.warn("RATE_LIMIT_429 ao criar nota no Manatal (após retries). Candidate: {}, Body: {}", candidateId, response.body());
                } else {
                    log.error("Erro ao criar nota no Manatal. Status: {}, Body: {}", response.statusCode(), response.body());
                }
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


            HttpResponse<String> response = sendWithRetry(request);

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


            HttpResponse<String> response = sendWithRetry(request);

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