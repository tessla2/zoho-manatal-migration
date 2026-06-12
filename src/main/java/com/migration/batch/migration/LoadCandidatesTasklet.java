package com.migration.batch.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import com.migration.entity.CandidateMigration;
import com.migration.repository.CandidateMigrationRepository;
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
    private final ZohoProperties zohoProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Step: loading candidates from Zoho with tag '{}'...", zohoProperties.tagName());

        String criteria = "Created_Time:between:" + zohoProperties.dateStart() + ":" + zohoProperties.dateEnd();
        int page = 1;
        int perPage = zohoProperties.pageSize();
        int totalLoaded = 0;

        try {
            while (true) {
                String json = zohoClientService.searchCandidates(criteria, page, perPage);
                JsonNode root = objectMapper.readTree(json);
                JsonNode data = root.path("data");

                if (!data.isArray() || data.isEmpty()) {
                    log.info("No more candidates found at page {}", page);
                    break;
                }

                for (JsonNode candidate : data) {
                    String id = candidate.path("id").asText();
                    String tag = candidate.path("Tag").asText("");

                    if (tag.contains(zohoProperties.tagName())) {
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
                }

                boolean hasMore = root.path("info").path("more_records").asBoolean(false);
                if (!hasMore) break;
                page++;
            }

            log.info("Load step complete. Total candidates loaded: {}", totalLoaded);
        } catch (Exception e) {
            log.error("Failed to load candidates from Zoho: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load candidates from Zoho", e);
        }

        return RepeatStatus.FINISHED;
    }
}
