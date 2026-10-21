import re
from collections import Counter
from pathlib import Path
from typing import List

from . import regras
from .modelo import ALVOS, Modelos
from .schemas import AnaliseResponse, Churn, Oportunidade, Sentimento

MODELOS_DIR = Path(__file__).resolve().parent.parent / "models"

STOPWORDS = set(
    "a o as os um uma de da do das dos em no na nos nas e é que para por com se mas ao à já foi ser "
    "tem têm não nao mais muito como sobre isso essa esse está estao estamos vamos pode podem cliente "
    "falou disse então entao também tambem pra pro eles elas nossa nosso pessoal hoje ontem texto orador falante".split()
)


def termos_principais(texto: str, limite: int = 8) -> List[str]:
    palavras = [p for p in re.findall(r"[a-zà-ú]{4,}", texto) if p not in STOPWORDS]
    return [p for p, _ in Counter(palavras).most_common(limite)]


def recomendar(nivel_churn: str, oportunidade: bool) -> str:
    if nivel_churn == "ALTO":
        return ("Acionar o gestor de contas em até 24h, abrir plano de retenção e priorizar "
                "a resolução dos problemas relatados.")
    if oportunidade:
        return "Encaminhar ao time comercial para enviar proposta e agendar demonstração do módulo de interesse."
    if nivel_churn == "MEDIO":
        return ("Agendar contato de acompanhamento com o suporte para confirmar que os pontos "
                "levantados foram resolvidos.")
    return "Manter o relacionamento regular e registrar a interação no CRM."


class Analisador:
    def __init__(self, diretorio_modelos: Path = MODELOS_DIR):
        self.modelos = Modelos(diretorio_modelos)

    def motores(self) -> dict:
        return {a: ("modelo" if self.modelos.tem(a) else "regras") for a in ALVOS}

    def analisar(self, texto: str) -> AnaliseResponse:
        t = " " + texto.lower() + " "
        m = self.modelos

        nome, confianca = m.produto_agregado(t)[:2] if m.tem("produto") else regras.produto(t)

        if m.tem("sentimento"):
            sent_label, sent_score = m.sentimento(t)
        else:
            sent_score = regras.sentimento_score(t)
            sent_label = "POSITIVO" if sent_score > 0.6 else "NEGATIVO" if sent_score < 0.4 else "NEUTRO"

        churn_score = m.binario("churn", t) if m.tem("churn") else regras.churn_score(t)
        nivel = "ALTO" if churn_score >= 0.6 else "MEDIO" if churn_score >= 0.3 else "BAIXO"

        if m.tem("oportunidade"):
            oport_score = m.binario("oportunidade", t)
            oport_existe = oport_score >= 0.5
        else:
            oport_score = regras.oportunidade_score(t)
            oport_existe = oport_score >= 0.3

        return AnaliseResponse(
            produto=nome,
            produtoConfianca=round(confianca, 2),
            sentimento=Sentimento(label=sent_label, score=round(sent_score, 2)),
            churn=Churn(nivel=nivel, score=round(churn_score, 2)),
            oportunidade=Oportunidade(existe=oport_existe, score=round(oport_score, 2)),
            termos=termos_principais(t),
            recomendacao=recomendar(nivel, oport_existe),
        )
