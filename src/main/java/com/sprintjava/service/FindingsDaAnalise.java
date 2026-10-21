package com.sprintjava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjava.model.Finding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public final class FindingsDaAnalise {

    private static final Set<String> SEVERIDADES = Set.of("low", "medium", "high", "critical");

    private FindingsDaAnalise() {
    }

    /**
     * @param tiposAbertos tipos de finding que o cliente já tem em aberto (não resolvidos nem descartados)
     */
    public static List<Finding> extrair(JsonNode finalReport, Long clientId, Long meetingId, Set<String> tiposAbertos) {
        List<Finding> resultado = new ArrayList<>();
        if (finalReport == null || !finalReport.path("findings").isArray()) {
            return resultado;
        }
        Set<String> vistos = new LinkedHashSet<>(tiposAbertos);
        for (JsonNode item : finalReport.get("findings")) {
            String tipo = item.path("finding_type").asText("").trim();
            String titulo = item.path("title").asText("").trim();
            if (tipo.isEmpty() || titulo.isEmpty() || !vistos.add(tipo)) {
                continue;
            }
            String severidade = item.path("severity_hint").asText("");
            resultado.add(new Finding(clientId, meetingId, tipo, titulo,
                    SEVERIDADES.contains(severidade) ? severidade : null));
        }
        return resultado;
    }
}
