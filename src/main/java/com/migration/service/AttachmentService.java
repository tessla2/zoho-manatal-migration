package com.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalAttachment;
import com.migration.model.ManatalResume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final FileStorageService fileStorageService;

    @Value("${migration.app.base-url}")
    private String appBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Long> downloadAndStore(String candidateId, String applicationId) {
        List<Long> storedIds = new ArrayList<>();
        try {
            String candidateAttachmentsJson = zohoClientService.listCandidateAttachments(candidateId);
            storedIds.addAll(parseAndSave(candidateId, candidateAttachmentsJson));
        } catch (Exception e) {
            log.warn("Could not fetch candidate attachments for {}: {}", candidateId, e.getMessage());
        }

        if (applicationId != null) {
            try {
                String appAttachmentsJson = zohoClientService.listApplicationAttachments(applicationId);
                storedIds.addAll(parseAndSave(candidateId, appAttachmentsJson));
            } catch (Exception e) {
                log.warn("Could not fetch application attachments for {}: {}", candidateId, e.getMessage());
            }
        }

        return storedIds;
    }

    public List<Long> parseAndSave(String candidateId, String attachmentsJson) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(attachmentsJson).path("data");
            for (JsonNode att : data) {
                String attachmentId = att.path("id").asText();
                if (attachmentId == null || attachmentId.isEmpty()) continue;

                String fileName = att.path("File_Name").asText(null);
                String fileType = att.path("File_Type").asText(null);
                String downloadUrl = att.path("download_url").asText(null);

                String finalFileName = fileName != null ? fileName : "attachment_" + attachmentId;
                String finalFileType = fileType != null ? fileType : "application/octet-stream";

                Long storedId = zohoClientService.saveAttachment(
                        candidateId, attachmentId, finalFileName, finalFileType, downloadUrl);
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

    public void postToManatal(String manatalCandidateId, List<Long> storedAttachmentIds) {
        if (storedAttachmentIds == null || storedAttachmentIds.isEmpty()) return;

        boolean resumePosted = false;

        for (Long attachmentId : storedAttachmentIds) {
            try {
                String fileName = fileStorageService.getFileName(attachmentId);
                String contentType = fileStorageService.getContentType(attachmentId);
                String fileUrl = appBaseUrl.replaceAll("/+$", "") + "/api/files/" + attachmentId;

                if (!resumePosted && isResumeFile(fileName, contentType)) {
                    ManatalResume resume = new ManatalResume();
                    resume.setResume_file(fileUrl);
                    manatalClientService.updateResume(manatalCandidateId, resume);
                    resumePosted = true;
                    log.info("Resume posted for candidate {}: {}", manatalCandidateId, fileName);
                }

                ManatalAttachment attachment = new ManatalAttachment();
                attachment.setName(fileName);
                attachment.setDescription("Migrated from Zoho");
                attachment.setFile(fileUrl);

                manatalClientService.createAttachment(manatalCandidateId, attachment);
                log.info("Attachment posted for candidate {}: {}", manatalCandidateId, fileName);
            } catch (Exception e) {
                log.warn("Failed to post attachment {} for candidate {}: {}", attachmentId, manatalCandidateId, e.getMessage());
            }
        }
    }

    private boolean isResumeFile(String fileName, String contentType) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".rtf") || lower.endsWith(".txt")
                || contentType.contains("pdf") || contentType.contains("document");
    }
}
