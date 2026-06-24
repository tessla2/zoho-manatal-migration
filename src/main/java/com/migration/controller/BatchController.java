package com.migration.controller;

import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.report.ReportService;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.repository.MigrationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Tag(name = "Batch", description = "Batch migration Spring Batch job management")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job candidateMigrationJob;
    private final ReportService reportService;
    private final MigrationLogRepository logRepository;
    private final CandidateMigrationRepository candidateMigrationRepository;

    @Operation(summary = "Start batch migration job",
            description = "Triggers the Spring Batch job: loadCandidatesStep → migrateCandidateStep → tagZohoStep")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Job started successfully"),
        @ApiResponse(responseCode = "500", description = "Error starting job", content = @Content)
    })
    @PostMapping("/run")
    public ResponseEntity<String> runMigration() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(candidateMigrationJob, params);
        return ResponseEntity.ok("Job iniciado com sucesso");
    }

    @Operation(summary = "Migration report", description = "Summary with success/error/pending totals")
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        return ResponseEntity.ok(reportService.generateSummary());
    }

    @Operation(summary = "Migration logs", description = "Lists all batch execution logs")
    @GetMapping("/logs")
    public ResponseEntity<List<MigrationLog>> getLogs() {
        return ResponseEntity.ok(logRepository.findAll());
    }

    @Operation(summary = "List candidates", description = "Paginated list of migrated candidates with optional status filter")
    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        Page<CandidateMigration> result = candidateMigrationRepository.findFiltered(
                status, search, org.springframework.data.domain.PageRequest.of(page, size));

        Map<String, Object> response = new HashMap<>();
        response.put("candidates", result.getContent());
        response.put("totalPages", result.getTotalPages());
        response.put("totalElements", result.getTotalElements());
        response.put("currentPage", result.getNumber());
        return ResponseEntity.ok(response);
    }
}
