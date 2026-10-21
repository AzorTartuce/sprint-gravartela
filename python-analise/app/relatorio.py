"""Monta o relatório de uma reunião no formato que o back Java persiste."""
from typing import Dict, List

from . import regras
from .analisador import Analisador

MAX_EVIDENCIAS = 2


def _evidencias(texto: str, chaves: List[str]) -> List[str]:
    achadas = []
    for linha in texto.splitlines():
        if any(c in " " + linha.lower() + " " for c in chaves):
            achadas.append(linha.strip()[:240])
        if len(achadas) == MAX_EVIDENCIAS:
            break
    return achadas


def _severidade_topico(ocorrencias: int) -> str:
    return "high" if ocorrencias >= 3 else "medium" if ocorrencias == 2 else "low"


def gerar_relatorio(texto: str, analisador: Analisador) -> Dict:
    a = analisador.analisar(texto)
    t = " " + texto.lower() + " "

    achados = []
    for tipo, (titulo, chaves) in regras.TOPICOS.items():
        n = regras.ocorrencias_topico(t, tipo)
        if n:
            achados.append({"finding_type": tipo, "title": titulo, "severity_hint": _severidade_topico(n)})
    if a.churn.nivel in ("ALTO", "MEDIO"):
        achados.append({
            "finding_type": "churn_risk",
            "title": "Risco de cancelamento ou troca de fornecedor",
            "severity_hint": "critical" if a.churn.nivel == "ALTO" else "medium"})
    if a.oportunidade.existe:
        achados.append({
            "finding_type": "expansion_opportunity",
            "title": "Oportunidade comercial identificada",
            "severity_hint": "medium" if a.oportunidade.score >= 0.6 else "low"})

    tipos = {x["finding_type"] for x in achados}
    agentes = ["sentimento"]
    if a.produto:
        agentes.append("produto")
    if "churn_risk" in tipos:
        agentes.append("churn")
    if "expansion_opportunity" in tipos:
        agentes.append("oportunidade")
    if tipos & {"technical_issue", "integration_gap", "usability_issue", "adoption_gap"}:
        agentes.append("suporte")
    if "pricing_concern" in tipos:
        agentes.append("comercial")

    reports = [{
        "agent": "sentimento",
        "resumo": f"Sentimento {a.sentimento.label} (score {a.sentimento.score}).",
        "evidencias": _evidencias(texto, regras.NEGATIVAS if a.sentimento.label == "NEGATIVO" else regras.POSITIVAS),
    }]
    if a.produto:
        reports.append({"agent": "produto", "resumo": f"Produto identificado: {a.produto} "
                        f"(confiança {a.produtoConfianca}).", "evidencias": []})
    if "churn_risk" in tipos:
        reports.append({"agent": "churn", "resumo": f"Risco de churn {a.churn.nivel} (score {a.churn.score}).",
                        "evidencias": _evidencias(texto, regras.CHURN)})
    if "expansion_opportunity" in tipos:
        reports.append({"agent": "oportunidade", "resumo": f"Oportunidade comercial (score {a.oportunidade.score}).",
                        "evidencias": _evidencias(texto, regras.OPORTUNIDADE)})
    for tipo in tipos & set(regras.TOPICOS):
        titulo, chaves = regras.TOPICOS[tipo]
        agente = "comercial" if tipo == "pricing_concern" else "suporte"
        reports.append({"agent": agente, "resumo": titulo, "evidencias": _evidencias(texto, chaves)})

    triage = (f"Reunião sobre {a.produto or 'produto não identificado'}: sentimento {a.sentimento.label.lower()}, "
              f"risco de churn {a.churn.nivel.lower()}, "
              f"oportunidade comercial {'sim' if a.oportunidade.existe else 'não'}. "
              f"{len(achados)} achado(s) identificado(s).")

    return {
        "triage": triage,
        "selected_agents": agentes,
        "reports": reports,
        "final_report": {
            "resumo": triage,
            "produto": a.produto,
            "produto_confianca": a.produtoConfianca,
            "sentimento": a.sentimento.model_dump(),
            "churn": a.churn.model_dump(),
            "oportunidade": a.oportunidade.model_dump(),
            "termos": a.termos,
            "recomendacao": a.recomendacao,
            "findings": achados,
        },
    }
