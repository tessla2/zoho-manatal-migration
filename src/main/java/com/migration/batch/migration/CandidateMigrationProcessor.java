package com.migration.batch.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.entity.CandidateMigration;
import com.migration.model.ManatalCandidate;
import com.migration.service.ZohoClientService;
import com.migration.transform.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateMigrationProcessor implements ItemProcessor<CandidateMigration, CandidateMigrationPackage> {

    private final ZohoClientService zohoClientService;
    private final CandidateMapper candidateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CandidateMigrationPackage process(CandidateMigration item) {

        CandidateMigrationPackage pkg = new CandidateMigrationPackage();
        pkg.setCandidateMigration(item);
        pkg.setZohoCandidateId(item.getZohoCandidateId());

        try {
            String zohoJson = zohoClientService.fetchCandidateById(item.getZohoCandidateId());
            JsonNode zohoData = objectMapper.readTree(zohoJson).path("data").get(0);

            ManatalCandidate transformed = candidateMapper.toManatal(zohoData);
            pkg.setTransformed(transformed);

            String noteInfo = candidateMapper.extractNoteInfo(zohoData);
            pkg.setNoteInfo(noteInfo);

            String linkedinUrl = candidateMapper.extractLinkedinUrl(zohoData);
            pkg.setLinkedinUrl(linkedinUrl);

            List<String> zohoNotes = fetchRealNotes(item.getZohoCandidateId());
            pkg.setZohoNotes(zohoNotes);

            String structuredInfo = candidateMapper.extractStructuredInfo(zohoData);
            pkg.setStructuredInfo(structuredInfo);

            List<String> interviewNotes = fetchRealInterviews(item.getZohoCandidateId());
            pkg.setInterviewNotes(interviewNotes);

            fetchApplicationsAndAttachments(item.getZohoCandidateId(), pkg);
            pkg.getCandidateMigration().setApplicationId(pkg.getApplicationId());

            return pkg;
        } catch (Exception e) {
            log.error("Error processing candidate {}: {}", item.getZohoCandidateId(), e.getMessage());
            pkg.setErrorMessage(e.getMessage());
            return pkg;
        }
    }

    private void fetchApplicationsAndAttachments(String zohoCandidateId, CandidateMigrationPackage pkg) {
        try {
            String appsJson = zohoClientService.listApplicationsByCandidate(zohoCandidateId);
            JsonNode appsData = objectMapper.readTree(appsJson).path("data");
            if (appsData.isEmpty()) {
                log.warn("No applications found for candidate {}", zohoCandidateId);
                return;
            }

            String applicationId = appsData.get(0).path("id").asText();
            pkg.setApplicationId(applicationId);
            log.info("Found application {} for candidate {}", applicationId, zohoCandidateId);

            List<Long> storedIds = new ArrayList<>();

            String candidateAttachmentsJson = zohoClientService.listCandidateAttachments(zohoCandidateId);
            storedIds.addAll(processAttachments(zohoCandidateId, candidateAttachmentsJson));

            String appAttachmentsJson = zohoClientService.listApplicationAttachments(applicationId);
            storedIds.addAll(processAttachments(zohoCandidateId, appAttachmentsJson));

            if (!storedIds.isEmpty()) {
                pkg.setStoredAttachmentIds(storedIds);
            }
        } catch (Exception e) {
            log.warn("Could not fetch attachments for candidate {}: {}", zohoCandidateId, e.getMessage());
        }
    }

    private List<String> fetchRealNotes(String zohoCandidateId) {
        List<String> notes = new ArrayList<>();
        try {
            String notesJson = zohoClientService.fetchCandidateNotes(zohoCandidateId);
            JsonNode data = objectMapper.readTree(notesJson).path("data");
            if (data.isArray()) {
                for (JsonNode note : data) {
                    String content = note.path("Note_Content").asText("");
                    String title = note.path("Note_Title").asText("");
                    String createdBy = note.path("Created_By").path("name").asText("");
                    String createdTime = note.path("Created_Time").asText("");

                    StringBuilder sb = new StringBuilder();
                    if (!title.isBlank()) sb.append("**").append(title).append("**\n\n");
                    sb.append(content);
                    if (!createdBy.isBlank()) sb.append("\n\n---\nBy: ").append(createdBy);
                    if (!createdTime.isBlank()) sb.append("\nDate: ").append(createdTime);

                    notes.add(sb.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch notes for candidate {}: {}", zohoCandidateId, e.getMessage());
        }
        return notes;
    }

    private List<String> fetchRealInterviews(String zohoCandidateId) {
        List<String> notes = new ArrayList<>();
        try {
            String interviewsJson = zohoClientService.fetchInterviewsByCandidate(zohoCandidateId);
            JsonNode data = objectMapper.readTree(interviewsJson).path("data");
            if (data.isArray()) {
                for (JsonNode interview : data) {
                    String interviewNote = candidateMapper.extractInterviewInfo(interview);
                    if (interviewNote != null) {
                        notes.add(interviewNote);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch interviews for candidate {}: {}", zohoCandidateId, e.getMessage());
        }
        return notes;
    }

    private List<Long> processAttachments(String candidateId, String attachmentsJson) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode data = objectMapper.readTree(attachmentsJson).path("data");
            for (JsonNode att : data) {
                String attachmentId = att.path("id").asText();
                String fileName = att.path("File_Name").asText(null);
                String fileType = att.path("File_Type").asText(null);
                String downloadUrl = att.path("download_url").asText(null);

                if (attachmentId == null || attachmentId.isEmpty()) continue;

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
            log.warn("Error processing attachments: {}", e.getMessage());
        }
        return ids;
    }
}
