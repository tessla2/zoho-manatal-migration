package com.migration.report;

import com.migration.entity.CandidateMigration;
import com.migration.entity.MigrationLog;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.repository.MigrationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CandidateMigrationRepository candidateRepository;
    private final MigrationLogRepository logRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public Map<String, Object> generateSummary() {
        long total = candidateRepository.count();
        List<CandidateMigration> sucessos = candidateRepository.findByStatus("SUCESSO");
        List<CandidateMigration> erros = candidateRepository.findByStatus("ERRO");
        List<CandidateMigration> pendentes = candidateRepository.findByStatus("PENDENTE");

        long sucesso = sucessos.size();
        long erro = erros.size();
        long pendente = pendentes.size();

        Map<String, Object> report = new HashMap<>();
        report.put("total", total);
        report.put("sucesso", sucesso);
        report.put("erro", erro);
        report.put("pendente", pendente);

        String taxa = total > 0 ? String.format("%.1f%%", (sucesso * 100.0 / total)) : "0%";
        report.put("taxa_sucesso", taxa);

        String ultimaExecucao = logRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .findFirst()
                .map(log -> log.getCreatedAt().format(DATE_FORMATTER))
                .orElse(null);
        report.put("ultimaExecucao", ultimaExecucao);

        List<CandidateMigration> topErroList = erros.stream()
                .sorted(Comparator.comparing(CandidateMigration::getUpdatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());

        List<Map<String, Object>> topErros = new ArrayList<>();
        for (CandidateMigration cm : topErroList) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("zohoId", cm.getZohoCandidateId());

            String step = logRepository.findByCandidateMigrationId(cm.getId())
                    .stream()
                    .sorted(Comparator.comparing(MigrationLog::getCreatedAt).reversed())
                    .findFirst()
                    .map(MigrationLog::getStep)
                    .orElse("");
            entry.put("step", step);
            entry.put("message", cm.getErrorMessage());
            entry.put("createdAt", cm.getCreatedAt() != null ? cm.getCreatedAt().format(DATE_FORMATTER) : null);
            topErros.add(entry);
        }
        report.put("topErros", topErros);

        Map<String, Long> errosPorTipo = erros.stream()
                .filter(c -> c.getErrorMessage() != null)
                .collect(Collectors.groupingBy(
                    c -> c.getErrorMessage().substring(0, Math.min(100, c.getErrorMessage().length())),
                    Collectors.counting()
                ));
        report.put("errosPorTipo", errosPorTipo);

        return report;
    }
}
