package com.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalAttachment;
import com.migration.model.ManatalResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final long MANATAL_MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final FileStorageService fileStorageService;

    @Value("${migration.app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Long> downloadAndStore(String candidateId, String applicationId) {
        List<Long> storedIds = new ArrayList<>();
        try {
            String candidateAttachmentsJson = zohoClientService.listCandidateAttachments(candidateId);
            storedIds.addAll(parseAndSave(candidateId, null, candidateAttachmentsJson));
        } catch (Exception e) {
            log.warn("Could not fetch candidate attachments for {}: {}", candidateId, e.getMessage());
        }

        if (applicationId != null) {
            try {
                String appAttachmentsJson = zohoClientService.listApplicationAttachments(applicationId);
                storedIds.addAll(parseAndSave(candidateId, applicationId, appAttachmentsJson));
            } catch (Exception e) {
                log.warn("Could not fetch application attachments for {}: {}", candidateId, e.getMessage());
            }
        }

        return storedIds;
    }

    public List<Long> parseAndSave(String candidateId, String applicationId, String attachmentsJson) {
        List<Long> ids = new ArrayList<>();
        Set<String> seenAttachmentIds = new HashSet<>();
        try {
            JsonNode data = objectMapper.readTree(attachmentsJson).path("data");
            for (JsonNode att : data) {
                String attachmentId = att.path("id").asText();
                if (attachmentId == null || attachmentId.isEmpty()) continue;
                if (!seenAttachmentIds.add(attachmentId)) {
                    log.debug("Skipping duplicate attachment {}", attachmentId);
                    continue;
                }

                String fileName = att.path("File_Name").asText(null);
                String fileType = att.path("File_Type").asText(null);
                String downloadUrl = att.path("download_url").asText(null);

                String finalFileName = fileName != null ? fileName : "attachment_" + attachmentId;
                String finalFileType = fileType != null ? fileType : "application/octet-stream";

                Long storedId = zohoClientService.saveAttachment(
                        candidateId, applicationId, attachmentId, finalFileName, finalFileType, downloadUrl);
                if (storedId != null) {
                    ids.add(storedId);
                    log.info("Saved attachment {} -> stored id {}", attachmentId, storedId);
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing attachments: {}", e.getMessage());
        }
        return ids;
    }

    public boolean postResumeSync(String manatalCandidateId, List<Long> storedAttachmentIds) {
        if (storedAttachmentIds == null || storedAttachmentIds.isEmpty()) return false;

        for (Long attachmentId : storedAttachmentIds) {
            String fileName = fileStorageService.getFileName(attachmentId);
            String contentType = fileStorageService.getContentType(attachmentId);

            if (isResumeFile(fileName, contentType)) {
                long fileSize = fileStorageService.getFileSize(attachmentId);
                if (fileSize > MANATAL_MAX_FILE_SIZE) {
                    log.warn("FILESIZE_EXCEEDED candidate {} ficheiro {} ({} bytes) excede 5MB do Manatal, a enviar mesmo assim", manatalCandidateId, fileName, fileSize);
                }
                String baseUrl = appBaseUrl.replaceAll("/+$", "") + "/api/files/" + attachmentId;
                ManatalResume resume = new ManatalResume();
                resume.setResume_file(baseUrl);
                manatalClientService.updateResume(manatalCandidateId, resume);
                log.info("Resume posted for candidate {}: {}", manatalCandidateId, fileName);
                return true;
            }
        }
        log.warn("No resume file found for candidate {} among {} stored attachments", manatalCandidateId,
                storedAttachmentIds != null ? storedAttachmentIds.size() : 0);
        return false;
    }

    @Async("attachmentExecutor")
    public CompletableFuture<Void> postToManatal(String manatalCandidateId, List<Long> storedAttachmentIds) {
        if (storedAttachmentIds == null || storedAttachmentIds.isEmpty())
            return CompletableFuture.completedFuture(null);

        Set<String> postedFileNames = new HashSet<>();

        for (Long attachmentId : storedAttachmentIds) {
            try {
                String fileName = fileStorageService.getFileName(attachmentId);
                String contentType = fileStorageService.getContentType(attachmentId);

                if (!postedFileNames.add(fileName)) {
                    log.info("Skipping duplicate filename for candidate {}: {}", manatalCandidateId, fileName);
                    continue;
                }

                if (isResumeFile(fileName, contentType)) {
                    continue;
                }

                long fileSize = fileStorageService.getFileSize(attachmentId);
                if (fileSize > MANATAL_MAX_FILE_SIZE) {
                    log.warn("FILESIZE_EXCEEDED candidate {} attachment {} ({} bytes) excede 5MB do Manatal, a enviar mesmo assim", manatalCandidateId, fileName, fileSize);
                }

                String baseUrl = appBaseUrl.replaceAll("/+$", "") + "/api/files/" + attachmentId;
                String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String fileUrl = baseUrl + "/" + encodedName;

                ManatalAttachment attachment = new ManatalAttachment();
                attachment.setName(fileName);
                attachment.setDescription("Migrated from Zoho");
                attachment.setFile(fileUrl);
                attachment.setCreator(1193857);

                manatalClientService.createAttachment(manatalCandidateId, attachment);
                log.info("Attachment posted for candidate {}: {}", manatalCandidateId, fileName);
            } catch (Exception e) {
                log.warn("Failed to post attachment {} for candidate {}: {}", attachmentId, manatalCandidateId, e.getMessage());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private boolean isResumeFile(String fileName, String contentType) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".rtf") || lower.endsWith(".txt")
                || lower.endsWith(".odt") || lower.endsWith(".dot") || lower.endsWith(".dotx")
                || lower.endsWith(".pages") || lower.endsWith(".tex"))
            return true;
        if (contentType != null && (contentType.contains("pdf") || contentType.contains("document")
                || contentType.contains("word") || contentType.contains("opendocument")))
            return true;
        if (lower.contains("cv") || lower.contains("resume") || lower.contains("curriculo")
                || lower.contains("curriculum") || lower.contains("bio"))
            return true;
        return false;
    }
}
