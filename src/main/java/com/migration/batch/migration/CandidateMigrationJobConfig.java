package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.exception.ApiException;
import com.migration.notification.BatchJobListener;
import com.migration.repository.CandidateMigrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor

// Configuração do Job de migração de candidatos, definindo os passos e componentes necessários para a execução do processo de migração
public class CandidateMigrationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CandidateMigrationRepository candidateMigrationRepository;
    private final LoadCandidatesTasklet loadCandidatesTasklet;
    private final CandidateMigrationProcessor processor;
    private final CandidateMigrationWriter writer;
    private final TagZohoTasklet tagZohoTasklet;
    private final BatchJobListener batchJobListener;

    @Value("${migration.batch.chunk-size:1}")
    private int chunkSize;

    @Value("${migration.batch.retry-limit:3}")
    private int retryLimit;

    @Value("${migration.batch.skip-limit:10}")
    private int skipLimit;

    @Bean
    public Job candidateMigrationJob() {
        return new JobBuilder("candidateMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(batchJobListener)
                .start(loadCandidatesStep())
                .next(migrateCandidateStep())
                .next(tagZohoStep())
                .build();
    }

    @Bean
    public Step loadCandidatesStep() {
        return new StepBuilder("loadCandidatesStep", jobRepository)
                .tasklet(loadCandidatesTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step tagZohoStep() {
        return new StepBuilder("tagZohoStep", jobRepository)
                .tasklet(tagZohoTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step migrateCandidateStep() {
        return new StepBuilder("migrateCandidateStep", jobRepository)
                .<CandidateMigration, CandidateMigrationPackage>chunk(chunkSize, transactionManager)
                .reader(candidateReader())
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(retryLimit)
                .retry(ApiException.class)
                .skipLimit(skipLimit)
                .skip(Exception.class)
                .noSkip(NullPointerException.class)
                .build();
    }

    @Bean
    public ItemReader<CandidateMigration> candidateReader() {
        RepositoryItemReader<CandidateMigration> reader = new RepositoryItemReader<>();
        reader.setRepository(candidateMigrationRepository);
        reader.setMethodName("findAll");
        reader.setArguments(List.of());
        reader.setSort(Map.of("id", Sort.Direction.ASC));
        reader.setPageSize(chunkSize);
        return reader;
    }
}
