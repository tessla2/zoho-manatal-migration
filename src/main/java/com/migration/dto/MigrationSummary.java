package com.migration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Batch migration report summary")
public class MigrationSummary {

    @Schema(description = "Total processed candidates", example = "150")
    private long total;

    @Schema(description = "Total successfully migrated candidates", example = "142")
    private long sucesso;

    @Schema(description = "Total failed candidates", example = "8")
    private long erro;

    @Schema(description = "Total pending candidates", example = "0")
    private long pendente;

    @Schema(description = "Success rate percentage", example = "94.67%")
    private String taxaSucesso;

    @Schema(description = "Last execution timestamp", example = "2026-06-15T10:30:00")
    private String ultimaExecucao;

    @Schema(description = "Errors grouped by type")
    private Map<String, Long> errosPorTipo;

    @Schema(description = "Top most frequent errors")
    private List<ErroDetail> topErros;

    @Data
    @Schema(description = "Migration error detail")
    public static class ErroDetail {
        @Schema(description = "Zoho candidate ID", example = "76333000000000001")
        private String zohoId;

        @Schema(description = "Step where the error occurred", example = "migrateCandidateStep")
        private String step;

        @Schema(description = "Error message", example = "Timeout calling Manatal API")
        private String message;
    }
}
