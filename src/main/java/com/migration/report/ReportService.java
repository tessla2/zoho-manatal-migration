package com.migration.report;

import com.migration.repository.CandidateMigrationRepository;
import com.migration.repository.MigrationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CandidateMigrationRepository candidateRepository;
    private final MigrationLogRepository logRepository;

    public Map<String, Object> generateSummary() {
        long total = candidateRepository.count();
        long sucesso = candidateRepository.findByStatus("SUCESSO").size();
        long erro = candidateRepository.findByStatus("ERRO").size();
        long pendente = candidateRepository.findByStatus("PENDENTE").size();

        Map<String, Object> report = new HashMap<>();
        report.put("total", total);
        report.put("sucesso", sucesso);
        report.put("erro", erro);
        report.put("pendente", pendente);

        String taxa = total > 0 ? String.format("%.1f%%", (sucesso * 100.0 / total)) : "0%";
        report.put("taxa_sucesso", taxa);

        return report;
    }
}
