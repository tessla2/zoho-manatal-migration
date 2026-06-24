package com.migration.controller;

import com.migration.repository.CandidateMigrationRepository;
import com.migration.service.ManatalClientService;
import com.migration.service.ZohoClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Application health check endpoints")
@Slf4j
public class HealthController {

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final CandidateMigrationRepository candidateMigrationRepository;

    @Operation(summary = "Health check", description = "Returns application health status including Zoho, Manatal and database connectivity")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        body.put("uptime", formatUptime(uptimeMs));

        String zohoStatus = checkZoho();
        body.put("zoho", zohoStatus);

        String manatalStatus = checkManatal();
        body.put("manatal", manatalStatus);

        body.put("zohoRateLimitRemaining", null);

        String dbStatus = checkDatabase();
        body.put("db", dbStatus);

        boolean allUp = "UP".equals(zohoStatus) && "UP".equals(manatalStatus) && "UP".equals(dbStatus);
        body.put("status", allUp ? "UP" : "DEGRADED");

        return ResponseEntity.ok(body);
    }

    private String checkZoho() {
        try {
            zohoClientService.fetchOneCandidate();
            return "UP";
        } catch (Exception e) {
            log.warn("Zoho health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkManatal() {
        try {
            manatalClientService.fetchOneCandidate();
            return "UP";
        } catch (Exception e) {
            log.warn("Manatal health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkDatabase() {
        try {
            candidateMigrationRepository.count();
            return "UP";
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private static String formatUptime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }
}
