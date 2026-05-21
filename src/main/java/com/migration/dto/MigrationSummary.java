package com.migration.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MigrationSummary {
    private long total;
    private long sucesso;
    private long erro;
    private long pendente;
    private String taxaSucesso;
    private String ultimaExecucao;
    private Map<String, Long> errosPorTipo;
    private List<ErroDetail> topErros;

    @Data
    public static class ErroDetail {
        private String zohoId;
        private String step;
        private String message;
    }
}
