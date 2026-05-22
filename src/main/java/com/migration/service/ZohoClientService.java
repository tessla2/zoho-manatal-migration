package com.migration.service;

import com.migration.config.ZohoProperties;
import com.migration.exception.ApiException;
import com.migration.model.RawZohoData;
import com.migration.model.StoredAttachment;
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



    //  Candidates //
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

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar candidato no Zoho: {}", e.getMessage(), e);
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

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar tags: {}", e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar tags no Zoho");
        }
    }

    public void tagCandidate(String candidateId) {
        try {
            String token = authService.generateAccessToken();
            String tagName = properties.tagName();
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

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar notas do candidato {}: {}", candidateId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao buscar notas no Zoho");
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
            String criteria = "Candidate.id:equals:" + candidateId;
            String url = properties.baseUrl() + "/Applications?criteria="
                    + java.net.URLEncoder.encode(criteria, java.nio.charset.StandardCharsets.UTF_8);

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

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao listar anexos da application {}: {}", applicationId, e.getMessage(), e);
            throw ApiException.badGateway("Falha ao listar anexos da application no Zoho");
        }
    }

}
