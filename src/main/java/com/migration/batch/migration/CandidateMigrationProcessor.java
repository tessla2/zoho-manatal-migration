package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.model.ManatalCandidate;
import com.migration.service.ZohoClientService;
import com.migration.transform.CandidateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

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

            return pkg;
        } catch (Exception e) {
            log.error("Error processing candidate {}: {}", item.getZohoCandidateId(), e.getMessage());
            pkg.setErrorMessage(e.getMessage());
            return pkg;
        }
    }
}
