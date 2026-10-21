package com.sprintjava.controller.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Contrato do front (frontend/frontsprint4). Usa camelCase, ao contrário do
 * restante da API (snake_case global), por isso cada record fixa a estratégia.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AnaliseTranscricaoResponse(
        String produto,
        double produtoConfianca,
        Sentimento sentimento,
        Churn churn,
        Oportunidade oportunidade,
        List<String> termos,
        String recomendacao) {

    /** label: POSITIVO | NEUTRO | NEGATIVO. */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Sentimento(String label, double score) {
    }

    /** nivel: BAIXO | MEDIO | ALTO. */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Churn(String nivel, double score) {
    }

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Oportunidade(boolean existe, double score) {
    }
}
