package com.migration.batch.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.exception.ApiException;
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
import java.util.concurrent.CompletableFuture;

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

                String manatalId = entity.getManatalCandidateId();
                if (manatalId == null || manatalId.isBlank()) {
                    String manatalResponse = manatalClientService.createCandidate(pkg.getTransformed());
                    log.info("Candidate {} migrated to Manatal: {}", pkg.getZohoCandidateId(), manatalResponse);
                    manatalId = objectMapper.readTree(manatalResponse).path("id").asText();
                } else {
                    log.info("Candidate {} already has Manatal ID {} in DB, reusing", pkg.getZohoCandidateId(), manatalId);
                }
                entity.setManatalCandidateId(manatalId);

                postAllNotes(manatalId, pkg);
                postSocialMedia(manatalId, pkg.getLinkedinUrl());
                postAttachments(entity, manatalId, pkg.getStoredAttachmentIds());

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

    private void postAllNotes(String manatalCandidateId, CandidateMigrationPackage pkg) {
        StringBuilder combined = new StringBuilder();

        List<String> zohoNotes = (pkg.getZohoNotes() != null && !pkg.getZohoNotes().isEmpty())
                ? pkg.getZohoNotes() : null;
        if (zohoNotes != null) {
            for (String note : zohoNotes) {
                combined.append(note).append("\n\n---\n\n");
            }
        } else if (pkg.getNoteInfo() != null && !pkg.getNoteInfo().isBlank()) {
            combined.append(pkg.getNoteInfo()).append("\n\n---\n\n");
        }

        if (pkg.getStructuredInfo() != null && !pkg.getStructuredInfo().isBlank()) {
            combined.append(pkg.getStructuredInfo()).append("\n\n---\n\n");
        }

        if (pkg.getInterviewNotes() != null && !pkg.getInterviewNotes().isEmpty()) {
            for (String note : pkg.getInterviewNotes()) {
                combined.append(note).append("\n\n---\n\n");
            }
        }

        String allNotes = combined.toString().trim();
        if (allNotes.isEmpty()) return;
        if (allNotes.endsWith("---")) {
            allNotes = allNotes.substring(0, allNotes.length() - 3).trim();
        }

        try {
            manatalClientService.createNote(manatalCandidateId, allNotes);
            log.info("All notes posted in single call for candidate {}", manatalCandidateId);
        } catch (ApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                log.warn("RATE_LIMIT_429 ao postar notas no batch para candidate {}: {}", manatalCandidateId, e.getMessage());
            } else {
                String msg = "Notas não postadas para candidate " + manatalCandidateId + ": " + e.getMessage();
                log.warn(msg);
                saveLog(pkg.getCandidateMigration(), "migrateCandidateStep", "AVISO", msg);
            }
        } catch (Exception e) {
            String msg = "Notas não postadas para candidate " + manatalCandidateId + ": " + e.getMessage();
            log.warn(msg);
            saveLog(pkg.getCandidateMigration(), "migrateCandidateStep", "AVISO", msg);
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

    private void postAttachments(CandidateMigration entity, String manatalCandidateId, List<Long> storedAttachmentIds) {
        if (storedAttachmentIds == null || storedAttachmentIds.isEmpty()) {
            throw new RuntimeException("Nenhum attachment encontrado (sem CV/resume) para o candidato " + entity.getZohoCandidateId());
        }
        boolean resumeFound = attachmentService.postResumeSync(manatalCandidateId, storedAttachmentIds);
        if (!resumeFound) {
            throw new RuntimeException("Nenhum ficheiro de CV/resume entre " + storedAttachmentIds.size() + " attachments para o candidato " + entity.getZohoCandidateId());
        }
        CompletableFuture<Void> future = attachmentService.postToManatal(manatalCandidateId, storedAttachmentIds);
        future.join();
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
