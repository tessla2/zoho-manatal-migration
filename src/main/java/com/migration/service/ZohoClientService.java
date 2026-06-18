package com.migration.service;

import com.migration.config.ZohoProperties;
import com.migration.exception.ApiException;
import com.migration.entity.RawZohoData;
import com.migration.entity.StoredAttachment;
import com.migration.notification.NotificationService;
import com.migration.repository.RawZohoDataRepository;
import com.migration.repository.StoredAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoClientService {

    private final ZohoAuthService authService;
    private final ZohoProperties properties;
    private final RawZohoDataRepository rawDataRepository;
    private final StoredAttachmentRepository storedAttachmentRepository;
    private final NotificationService notificationService;

    private final java.util.concurrent.atomic.AtomicInteger zohoRateLimitRemaining = new java.util.concurrent.atomic.AtomicInteger(-1);
    private final java.util.concurrent.atomic.AtomicInteger zohoRateLimitLimit = new java.util.concurrent.atomic.AtomicInteger(-1);

    private int zohoRateLimitThreshold = 100;
    private int zohoRateLimitCriticalThreshold = 20;
    private int zohoRateLimitHaltThreshold = 5;
    private long zohoRateLimitWaitMs = 60_000;

    @org.springframework.beans.factory.annotation.Value("${migration.zoho.rate-limit-threshold:100}")
    public void setZohoRateLimitThreshold(int threshold) {
        this.zohoRateLimitThreshold = threshold;
    }

    @org.springframework.beans.factory.annotation.Value("${migration.zoho.rate-limit-critical-threshold:20}")
    public void setZohoRateLimitCriticalThreshold(int threshold) {
        this.zohoRateLimitCriticalThreshold = threshold;
    }

    @org.springframework.beans.factory.annotation.Value("${migration.zoho.rate-limit-halt-threshold:5}")
    public void setZohoRateLimitHaltThreshold(int threshold) {
        this.zohoRateLimitHaltThreshold = threshold;
    }

    @org.springframework.beans.factory.annotation.Value("${migration.zoho.rate-limit-wait-ms:60000}")
    public void setZohoRateLimitWaitMs(long waitMs) {
        this.zohoRateLimitWaitMs = waitMs;
    }

    void checkZohoRateLimit(java.net.http.HttpResponse<?> response) {
        response.headers().firstValue("X-RATE-LIMIT-REMAINING").ifPresent(v -> {
            int remaining = Integer.parseInt(v);
            zohoRateLimitRemaining.set(remaining);

            if (remaining <= zohoRateLimitHaltThreshold) {
                String msg = String.format(
                    "Zoho API: apenas %d créditos restantes (halt threshold: %d). A interromper operação para evitar falha total.",
                    remaining, zohoRateLimitHaltThreshold);
                log.error(msg);
                notificationService.sendAlert("Zoho Rate Limite Crítico", msg);
                throw new RuntimeException(msg);
            }

            if (remaining < zohoRateLimitCriticalThreshold) {
                String msg = String.format(
                    "Apenas %d créditos Zoho restantes (critical threshold: %d). A aguardar %dms antes de continuar...",
                    remaining, zohoRateLimitCriticalThreshold, zohoRateLimitWaitMs);
                log.warn(msg);
                notificationService.sendAlert("Zoho Rate Limit Crítico", msg);
                try {
                    Thread.sleep(zohoRateLimitWaitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }

            if (remaining < zohoRateLimitThreshold) {
                String msg = String.format("Apenas %d créditos Zoho restantes (threshold: %d)", remaining, zohoRateLimitThreshold);
                log.warn(msg);
                notificationService.sendAlert("Zoho Rate Limit Baixo", msg);
            }
        });
        response.headers().firstValue("X-RATE-LIMIT-LIMIT").ifPresent(v ->
            zohoRateLimitLimit.set(Integer.parseInt(v))
        );
    }

    public int getZohoRateLimitRemaining() {
        return zohoRateLimitRemaining.get();
    }

    public int getZohoRateLimitLimit() {
        return zohoRateLimitLimit.get();
    }

    //  Candidates //
    public String listCandidates(int page, int perPage) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates?page=" + page + "&per_page=" + perPage;

            log.info("Listing candidates page {}: {}", page, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("List candidates status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao listar candidatos. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao listar candidatos");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar candidatos: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Zoho");
        }
    }

    public String fetchCandidateById(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId;

            log.info("Chamando URL para candidato: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Status code: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Zoho: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Zoho");
        }
    }

    public String fetchOneCandidate() {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates?page=1&per_page=10";

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

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Zoho: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Zoho");
        }
    }

    public String searchCandidates(String criteria, int page, int perPage) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/search?criteria="
                    + java.net.URLEncoder.encode(criteria, java.nio.charset.StandardCharsets.UTF_8)
                    + "&page=" + page + "&per_page=" + perPage;

            log.info("Searching candidates: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Search status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao pesquisar candidatos. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao pesquisar candidatos: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha na comunicação com a API do Zoho");
        }
    }

    @Transactional
    public String fetchAndSaveCandidates() {
        String rawJson = fetchOneCandidate();

        RawZohoData entity = new RawZohoData();
        entity.setModule("Candidates");
        entity.setRawJson(rawJson);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setProcessed(false);

        rawDataRepository.save(entity);
        log.info("Raw candidate data saved with id: {}", entity.getId());

        return rawJson;
    }

    // Attachments //

    public String listCandidateAttachments(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/Attachments";

            log.info("Listing attachments for candidate {}: {}", candidateId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Attachments list status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao listar anexos. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao listar anexos");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar anexos do candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar anexos no Zoho");
        }
    }


    public byte[] downloadAttachment(String attachmentId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Attachments/" + attachmentId;

            log.info("Downloading attachment {}: {}", attachmentId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.info("Download attachment status: {}", response.statusCode());

            if (response.statusCode() == 204 || response.statusCode() == 404) {
                log.warn("Anexo {} sem conteúdo (status {}). URL: {}", attachmentId, response.statusCode(), url);
                return new byte[0];
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body());
                log.error("Erro ao baixar anexo {}. Status: {}, Body: {}", attachmentId, response.statusCode(), body);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao baixar anexo");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao baixar anexo {}: {}", attachmentId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao baixar anexo do Zoho");
        }
    }


    public byte[] downloadAttachmentFromUrl(String downloadUrl) {
        try {
            String token = authService.generateAccessToken();

            log.info("Downloading attachment from URL: {}", downloadUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.info("Download from URL status: {}", response.statusCode());

            if (response.statusCode() == 204 || response.statusCode() == 404) {
                log.warn("Anexo sem conteúdo (status {})", response.statusCode());
                return new byte[0];
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body());
                log.error("Erro ao baixar anexo. Status: {}, Body: {}", response.statusCode(), body);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao baixar anexo");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao baixar anexo de URL: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha ao baixar anexo do Zoho");
        }
    }

    @Transactional
    public Long saveAttachment(String candidateId, String attachmentId, String fileName, String fileType, String downloadUrl) {
        byte[] data;
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            data = downloadAttachmentFromUrl(downloadUrl);
        } else {
            data = downloadAttachment(attachmentId);
        }
        if (data.length == 0) {
            log.warn("Anexo {} vazio, não será salvo", attachmentId);
            return null;
        }

        StoredAttachment attachment = new StoredAttachment();
        attachment.setZohoAttachmentId(attachmentId);
        attachment.setCandidateId(candidateId);
        attachment.setFileName(fileName);
        attachment.setFileType(fileType);
        attachment.setFileSize((long) data.length);
        attachment.setData(data);
        attachment.setCreatedAt(LocalDateTime.now());

        storedAttachmentRepository.save(attachment);
        log.info("Attachment saved to DB with id: {}", attachment.getId());

        return attachment.getId();
    }

    // Interviews //
    public String fetchOneInterview() {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Interviews?page=1&per_page=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Status code: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar interview no Zoho: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha ao buscar interview no Zoho");
        }

    }

    // Tags //
    public String listTags(String module) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/settings/tags?module=" + module;

            log.info("Fetching tags for module {}: {}", module, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("List tags status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao listar tags. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao listar tags");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar tags: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar tags no Zoho");
        }
    }

    public void tagCandidate(String candidateId) {
        tagCandidateWithTag(candidateId, properties.successTagName());
    }

    public void tagCandidateWithTag(String candidateId, String tagName) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/actions/add_tags?tag_names="
                    + java.net.URLEncoder.encode(tagName, java.nio.charset.StandardCharsets.UTF_8);

            log.info("Tagging candidate {} with tag '{}': {}", candidateId, tagName, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Tag candidate status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao marcar candidato. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao marcar candidato");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao marcar candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao marcar candidato no Zoho");
        }
    }

    public void removeTagFromCandidate(String candidateId, String tagName) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/actions/remove_tags?tag_names="
                    + java.net.URLEncoder.encode(tagName, java.nio.charset.StandardCharsets.UTF_8);

            log.info("Removing tag '{}' from candidate {}: {}", tagName, candidateId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Remove tag status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao remover tag '{}' do candidato {}. Status: {}, Body: {}", tagName, candidateId, response.statusCode(), response.body());
                throw ApiException.badGateway("Falha ao remover tag '" + tagName + "' do candidato " + candidateId + ": " + response.body());
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao remover tag '{}' do candidato {}: {}", tagName, candidateId, e.getMessage());
            throw ApiException.badGateway("Falha ao remover tag '" + tagName + "' do candidato " + candidateId + ": " + e.getMessage());
        }
    }

    // Notes //
    public String fetchCandidateNotes(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/Notes";

            log.info("Fetching notes for candidate {}: {}", candidateId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Notes fetch status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao buscar notas. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao buscar notas");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar notas do candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao buscar notas no Zoho");
        }
    }

    // Interviews //
    public String fetchInterviewsByCandidate(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/Interviews";

            log.info("Fetching interviews for candidate {}: {}", candidateId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Interviews fetch status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao buscar entrevistas. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao buscar entrevistas");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar entrevistas do candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao buscar entrevistas no Zoho");
        }
    }

    // Applications //
    public String fetchOneApplication() {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Applications?page=1&per_page=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Status code: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar application no Zoho: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha ao buscar application no Zoho");
        }
    }

    public String listApplicationsByCandidate(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Candidates/" + candidateId + "/Applications";

            log.info("Listing applications for candidate {}: {}", candidateId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Applications list status: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Erro ao listar applications. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode() + " ao listar applications");
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar applications do candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar applications no Zoho");
        }
    }

    public String listApplicationAttachments(String applicationId) {
        try {
            String token = authService.generateAccessToken();
            String url = properties.baseUrl() + "/Applications/" + applicationId + "/Attachments";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Zoho-oauthtoken " + token)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Status code: {}", response.statusCode());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Resposta não esperada da API Zoho. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Zoho API retornou status " + response.statusCode());
            }

            this.checkZohoRateLimit(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar anexos da application {}: {}", applicationId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar anexos da application no Zoho");
        }
    }

}
