package com.migration.batch.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.exception.ApiException;
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

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadCandidatesTasklet implements Tasklet {

    private final ZohoClientService zohoClientService;
    private final CandidateMigrationRepository repository;
    private final MigrationLogRepository logRepository;
    private final ZohoProperties zohoProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Step: loading candidates from Zoho with tag '{}'...", zohoProperties.tagName());

        int page = 1;
        int perPage = zohoProperties.pageSize();
        int totalLoaded = 0;
        int maxRetries = 3;

        try {
            while (true) {
                String json = listWithRetry(page, perPage, maxRetries);
                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.path("data");

                if (!data.isArray() || data.isEmpty()) {
                    log.info("No more candidates found at page {}", page);
                    break;
                }

                for (JsonNode candidate : data) {
                    String id = candidate.path("id").asText();

                    JsonNode tagArray = candidate.path("Associated_Tags");
                    boolean hasTag = false;
                    if (tagArray.isArray()) {
                        for (JsonNode t : tagArray) {
                            String tagName = t.isTextual() ? t.asText() : t.path("name").asText("");
                            if (tagName.equals(zohoProperties.tagName())) {
                                hasTag = true;
                                break;
                            }
                        }
                    }
                    if (!hasTag) continue;

                    Optional<CandidateMigration> existing = repository.findByZohoCandidateId(id);
                    if (existing.isEmpty()) {
                        CandidateMigration entity = new CandidateMigration();
                        entity.setZohoCandidateId(id);
                        entity.setStatus("PENDENTE");
                        repository.save(entity);
                        totalLoaded++;
                        log.info("Loaded candidate {} as PENDENTE", id);
                    } else {
                        log.debug("Candidate {} already exists in DB, skipping", id);
                    }
                }

                boolean hasMore = root.path("info").path("more_records").asBoolean(false);
                if (!hasMore) break;
                page++;
            }

            log.info("Load step complete. Total candidates loaded: {}", totalLoaded);
        } catch (ApiException e) {
            log.error("Zoho API error during load step after {} retries: {}", maxRetries, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Unexpected error during load step (continuing with partial data): {}", e.getMessage());
        }

        saveLoadLog(totalLoaded);
        return RepeatStatus.FINISHED;
    }

    private String listWithRetry(int page, int perPage, int maxRetries) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return zohoClientService.listCandidates(page, perPage);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed for page {}: {}", attempt, maxRetries, page, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ApiException.internalError("Interrupted during retry backoff", ie);
                    }
                }
            }
        }
        throw ApiException.badGateway("Failed to load page " + page + " after " + maxRetries + " attempts: "
                + lastException.getMessage());
    }

    private void saveLoadLog(int totalLoaded) {
        MigrationLog ml = new MigrationLog();
        ml.setStep("loadCandidatesStep");
        ml.setStatus(totalLoaded > 0 ? "SUCESSO" : "SEM_NOVOS");
        ml.setMessage("Loaded " + totalLoaded + " candidates from Zoho with tag '" + zohoProperties.tagName() + "'");
        logRepository.save(ml);
    }
}
