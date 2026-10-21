package com.sprintjava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintjava.model.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingsDaAnaliseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void converteAchadosDoRelatorio() throws Exception {
        var relatorio = mapper.readTree("""
                {"findings":[
                  {"finding_type":"technical_issue","title":"Problemas técnicos","severity_hint":"high"},
                  {"finding_type":"churn_risk","title":"Risco de churn","severity_hint":"critical"}]}""");

        List<Finding> achados = FindingsDaAnalise.extrair(relatorio, 7L, 42L, Set.of());

        assertEquals(2, achados.size());
        assertEquals(7L, achados.get(0).getClientId());
        assertEquals(42L, achados.get(0).getOriginMeetingId());
        assertEquals("high", achados.get(0).getSeverityHint());
        assertEquals(Finding.STATUS_AWAITING_STRATEGY, achados.get(0).getStatus());
    }

    @Test
    void ignoraTiposJaAbertosRepetidosEInvalidos() throws Exception {
        var relatorio = mapper.readTree("""
                {"findings":[
                  {"finding_type":"technical_issue","title":"Já aberto"},
                  {"finding_type":"pricing_concern","title":"Preço","severity_hint":"urgente"},
                  {"finding_type":"pricing_concern","title":"Preço de novo"},
                  {"finding_type":"","title":"Sem tipo"},
                  {"finding_type":"adoption_gap","title":""}]}""");

        List<Finding> achados = FindingsDaAnalise.extrair(relatorio, 1L, 2L, Set.of("technical_issue"));

        assertEquals(1, achados.size());
        assertEquals("pricing_concern", achados.get(0).getFindingType());
        assertNull(achados.get(0).getSeverityHint());
    }

    @Test
    void relatorioSemAchadosRetornaListaVazia() throws Exception {
        assertTrue(FindingsDaAnalise.extrair(mapper.readTree("{}"), 1L, 2L, Set.of()).isEmpty());
        assertTrue(FindingsDaAnalise.extrair(null, 1L, 2L, Set.of()).isEmpty());
    }
}
