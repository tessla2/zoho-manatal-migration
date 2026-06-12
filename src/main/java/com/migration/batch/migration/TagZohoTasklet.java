package com.migration.batch.migration;

import com.migration.config.ZohoProperties;
import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.repository.MigrationLogRepository;
import com.migration.service.ZohoClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagZohoTasklet implements Tasklet {

    private final CandidateMigrationRepository repository;
    private final MigrationLogRepository logRepository;
    private final ZohoClientService zohoClientService;
    private final ZohoProperties zohoProperties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Step: tagging successful candidates in Zoho...");

        List<CandidateMigration> candidates = repository.findSuccessWithoutTag("SUCESSO");

        if (candidates.isEmpty()) {
            log.info("No candidates to tag");
            return RepeatStatus.FINISHED;
        }

        log.info("Found {} candidates to tag", candidates.size());

        for (CandidateMigration candidate : candidates) {
            try {
                zohoClientService.tagCandidateWithTag(candidate.getZohoCandidateId(), zohoProperties.successTagName());
                zohoClientService.removeTagFromCandidate(candidate.getZohoCandidateId(), zohoProperties.tagName());
                candidate.setTaggedInZoho(true);
                repository.save(candidate);
                log.info("Candidate {} tagged successfully", candidate.getZohoCandidateId());
                saveLog(candidate, "tagZohoStep", "SUCESSO", "Tagged in Zoho, removed " + zohoProperties.tagName());
            } catch (Exception e) {
                log.error("Failed to tag candidate {}: {}", candidate.getZohoCandidateId(), e.getMessage());
                candidate.setStatus("ERRO");
                candidate.setErrorMessage("Falha ao marcar no Zoho: " + e.getMessage());
                repository.save(candidate);
                saveLog(candidate, "tagZohoStep", "ERRO", e.getMessage());
            }
        }

        return RepeatStatus.FINISHED;
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
