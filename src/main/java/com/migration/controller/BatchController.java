package com.migration.controller;

import com.migration.report.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job candidateMigrationJob;
    private final ReportService reportService;

    @PostMapping("/run")
    public ResponseEntity<String> runMigration() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(candidateMigrationJob, params);
            return ResponseEntity.ok("Job iniciado com sucesso");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao iniciar job: " + e.getMessage());
        }
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        return ResponseEntity.ok(reportService.generateSummary());
    }
}
