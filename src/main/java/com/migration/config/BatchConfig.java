package com.migration.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfig {

    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public JobRepository jobRepository() {
        try {
            var factory = new org.springframework.batch.core.repository.support.JobRepositoryFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTransactionManager(transactionManager);
            factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
            factory.setTablePrefix("BATCH_");
            factory.afterPropertiesSet();
            return factory.getObject();
        } catch (Exception e) {
            log.error("Failed to create JobRepository", e);
            throw new BeanCreationException("jobRepository", e.getMessage(), e);
        }
    }

    @Bean
    public JobLauncher jobLauncher(JobRepository jobRepository) {
        var launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor());
        return launcher;
    }

    @Bean
    public JobExplorer jobExplorer() {
        try {
            var factory = new org.springframework.batch.core.explore.support.JobExplorerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTablePrefix("BATCH_");
            factory.afterPropertiesSet();
            return factory.getObject();
        } catch (Exception e) {
            log.error("Failed to create JobExplorer", e);
            throw new BeanCreationException("jobExplorer", e.getMessage(), e);
        }
    }
}
