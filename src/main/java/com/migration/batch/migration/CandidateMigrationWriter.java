package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.service.ManatalClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateMigrationWriter implements ItemWriter<CandidateMigrationPackage> {

    private final ManatalClientService manatalClientService;
    private final CandidateMigrationRepository repository;

    @Override
    public void write(Chunk<? extends CandidateMigrationPackage> chunk) {
        for (CandidateMigrationPackage pkg : chunk) {
            CandidateMigration entity = pkg.getCandidateMigration();

            try {
                if (pkg.getErrorMessage() != null) {
                    entity.setStatus("ERRO");
                    entity.setErrorMessage(pkg.getErrorMessage());
                    repository.save(entity);
                    continue;
                }

                String manatalResponse = manatalClientService.createCandidate(pkg.getTransformed());
                log.info("Candidate {} migrated to Manatal: {}", pkg.getZohoCandidateId(), manatalResponse);

                entity.setStatus("SUCESSO");
                repository.save(entity);
            } catch (Exception e) {
                log.error("Error writing candidate {}: {}", pkg.getZohoCandidateId(), e.getMessage());
                entity.setStatus("ERRO");
                entity.setErrorMessage(e.getMessage());
                repository.save(entity);
            }
        }
    }
}
