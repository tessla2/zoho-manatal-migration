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
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
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

    @Operation(summary = "Migration warnings/errors", description = "Lists AVISO and ERRO logs only")
    @GetMapping("/logs/warnings")
    public ResponseEntity<List<MigrationLog>> getWarnings() {
        return ResponseEntity.ok(logRepository.findWarnings());
    }

    @Operation(summary = "List candidates", description = "Paginated list of migrated candidates with optional status filter")
    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size, Sort.by("id"));
        Page<CandidateMigration> result;
        if (status == null && search == null) {
            result = candidateMigrationRepository.findAll(pageable);
        } else {
            result = candidateMigrationRepository.findFiltered(status, search, pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("candidates", result.getContent());
        response.put("totalPages", result.getTotalPages());
        response.put("totalElements", result.getTotalElements());
        response.put("currentPage", result.getNumber());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(response);
    }

    @Operation(summary = "Recent candidates", description = "Last 50 candidates ordered by id descending")
    @GetMapping("/candidates/recent")
    public ResponseEntity<List<CandidateMigration>> getRecentCandidates() {
        return ResponseEntity.ok(candidateMigrationRepository.findTop50ByOrderByIdDesc());
    }

    @Operation(summary = "Reset errors to PENDENTE", description = "Resets all ERRO candidates back to PENDENTE for retry")
    @PostMapping("/reset-erros")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> resetErros() {
        int count = candidateMigrationRepository.resetErrosParaPendente();
        Map<String, Object> response = new HashMap<>();
        response.put("resetados", count);
        response.put("mensagem", count + " candidatos com erro resetados para PENDENTE");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Daily migration stats", description = "Aggregated stats per day")
    @GetMapping("/stats/daily")
    public ResponseEntity<List<Map<String, Object>>> getDailyStats() {
        List<Object[]> rows = candidateMigrationRepository.findDailyStats();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("day", row[0] != null ? row[0].toString() : null);
            day.put("total", row[1] != null ? ((Number) row[1]).intValue() : 0);
            day.put("sucesso", row[2] != null ? ((Number) row[2]).intValue() : 0);
            day.put("erro", row[3] != null ? ((Number) row[3]).intValue() : 0);
            day.put("pendente", row[4] != null ? ((Number) row[4]).intValue() : 0);
            result.add(day);
        }
        return ResponseEntity.ok(result);
    }
}
