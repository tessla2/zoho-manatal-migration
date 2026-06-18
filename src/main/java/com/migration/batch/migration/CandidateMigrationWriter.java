package com.migration.batch.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.repository.MigrationLogRepository;
import com.migration.service.AttachmentService;
import com.migration.service.ManatalClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateMigrationWriter implements ItemWriter<CandidateMigrationPackage> {

    private final ManatalClientService manatalClientService;
    private final CandidateMigrationRepository repository;
    private final MigrationLogRepository logRepository;
    private final AttachmentService attachmentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void write(Chunk<? extends CandidateMigrationPackage> chunk) {
        for (CandidateMigrationPackage pkg : chunk) {
            CandidateMigration entity = pkg.getCandidateMigration();

            try {
                if (pkg.getErrorMessage() != null) {
                    entity.setStatus("ERRO");
                    entity.setErrorMessage(pkg.getErrorMessage());
                    repository.save(entity);
                    saveLog(entity, "migrateCandidateStep", "ERRO", pkg.getErrorMessage());
                    continue;
                }

                String manatalResponse = manatalClientService.createCandidate(pkg.getTransformed());
                log.info("Candidate {} migrated to Manatal: {}", pkg.getZohoCandidateId(), manatalResponse);

                String manatalId = objectMapper.readTree(manatalResponse).path("id").asText();
                entity.setManatalCandidateId(manatalId);

                postNotes(manatalId, pkg.getZohoNotes(), pkg.getNoteInfo());
                postStructuredInfo(manatalId, pkg.getStructuredInfo());
                postInterviewNotes(manatalId, pkg.getInterviewNotes());
                postSocialMedia(manatalId, pkg.getLinkedinUrl());
                postAttachments(manatalId, pkg.getStoredAttachmentIds());

                entity.setStatus("SUCESSO");
                repository.save(entity);
                saveLog(entity, "migrateCandidateStep", "SUCESSO", "Candidate " + pkg.getZohoCandidateId() + " -> Manatal " + manatalId);
            } catch (Exception e) {
                log.error("Error writing candidate {}: {}", pkg.getZohoCandidateId(), e.getMessage());
                entity.setStatus("ERRO");
                entity.setErrorMessage(e.getMessage());
                repository.save(entity);
                saveLog(entity, "migrateCandidateStep", "ERRO", e.getMessage());
            }
        }
    }

    private void postStructuredInfo(String manatalCandidateId, String structuredInfo) {
        if (structuredInfo == null || structuredInfo.isBlank()) return;
        try {
            manatalClientService.createNote(manatalCandidateId, structuredInfo);
            log.info("Structured info posted for candidate {}", manatalCandidateId);
        } catch (Exception e) {
            log.warn("Failed to post structured info for candidate {}: {}", manatalCandidateId, e.getMessage());
        }
    }

    private void postInterviewNotes(String manatalCandidateId, List<String> interviewNotes) {
        if (interviewNotes == null || interviewNotes.isEmpty()) return;
        for (String note : interviewNotes) {
            try {
                manatalClientService.createNote(manatalCandidateId, note);
                log.info("Interview note posted for candidate {}", manatalCandidateId);
            } catch (Exception e) {
                log.warn("Failed to post interview note for candidate {}: {}", manatalCandidateId, e.getMessage());
            }
        }
    }

    private void postSocialMedia(String manatalCandidateId, String linkedinUrl) {
        if (linkedinUrl == null || linkedinUrl.isBlank()) return;
        try {
            manatalClientService.createSocialMedia(manatalCandidateId, "linkedin", linkedinUrl);
            log.info("LinkedIn posted for candidate {}", manatalCandidateId);
        } catch (Exception e) {
            log.warn("Failed to post LinkedIn for candidate {}: {}", manatalCandidateId, e.getMessage());
        }
    }

    private void postAttachments(String manatalCandidateId, List<Long> storedAttachmentIds) {
        attachmentService.postToManatal(manatalCandidateId, storedAttachmentIds);
    }

    private void postNotes(String manatalCandidateId, List<String> zohoNotes, String fallbackNote) {
        List<String> notes = (zohoNotes != null && !zohoNotes.isEmpty()) ? zohoNotes : null;
        if (notes == null) {
            if (fallbackNote != null && !fallbackNote.isBlank()) {
                notes = List.of(fallbackNote);
            } else {
                return;
            }
        }
        for (String note : notes) {
            try {
                manatalClientService.createNote(manatalCandidateId, note);
                log.info("Note posted for candidate {}", manatalCandidateId);
            } catch (Exception e) {
                log.warn("Failed to post note for candidate {}: {}", manatalCandidateId, e.getMessage());
            }
        }
    }

    private void saveLog(CandidateMigration entity, String step, String status, String message) {
        try {
            MigrationLog ml = new MigrationLog();
            ml.setCandidateMigrationId(entity.getId());
            ml.setStep(step);
            ml.setStatus(status);
            ml.setMessage(message);
            logRepository.save(ml);
        } catch (Exception e) {
            log.warn("Failed to save migration log: {}", e.getMessage());
        }
    }

}
