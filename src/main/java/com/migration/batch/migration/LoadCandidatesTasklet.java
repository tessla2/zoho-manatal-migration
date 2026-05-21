package com.migration.batch.migration;

import com.migration.repository.CandidateMigrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadCandidatesTasklet implements Tasklet {

    private final CandidateMigrationRepository repository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Step: checking for pending candidates...");

        long pending = repository.findByStatus("PENDENTE").size();
        log.info("Found {} pending candidates", pending);

        return RepeatStatus.FINISHED;
    }
}
