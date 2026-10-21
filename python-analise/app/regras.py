"""Motor provisório por palavras-chave.

Usado apenas para os indicadores que ainda não têm modelo treinado
(ver modelo.py). Não é machine learning.
"""
from typing import List, Optional, Tuple

PRODUTOS = {
    "Protheus": ["protheus"],
    "RM": [" rm ", "totvs rm", "folha de pagamento", "rh "],
    "Fluig": ["fluig"],
    "Datasul": ["datasul"],
    "Winthor": ["winthor"],
}
POSITIVAS = ["ótimo", "otimo", "excelente", "satisfeito", "feliz", "gostei", "parabéns",
             "resolveu", "funciona bem", "obrigado", "ajudou"]
NEGATIVAS = ["lento", "lentidão", "problema", "erro", "falha", "insatisfeito", "ruim", "péssimo",
             "pessimo", "demora", "travando", "reclamação", "reclamacao", "caro", "cancelar"]
CHURN = ["cancelar", "cancelamento", "concorrente", "outro fornecedor", "migrar",
         "trocar de sistema", "rescindir", "não vamos renovar", "nao vamos renovar", "insatisfeito"]
OPORTUNIDADE = ["orçamento", "orcamento", "proposta", "contratar", "novo módulo", "novo modulo",
                "expandir", "ampliar", "interesse", "licenças adicionais", "licencas adicionais",
                "upgrade", "filial"]


def _contar(texto: str, chaves: List[str]) -> int:
    return sum(1 for c in chaves if c in texto)


def _limitar(v: float) -> float:
    return max(0.0, min(1.0, v))


def produto(texto: str) -> Tuple[Optional[str], float]:
    melhor_nome, melhor = None, 0
    for nome, chaves in PRODUTOS.items():
        c = _contar(texto, chaves)
        if c > melhor:
            melhor_nome, melhor = nome, c
    return melhor_nome, (_limitar(0.55 + melhor * 0.15) if melhor_nome else 0.0)


def sentimento_score(texto: str) -> float:
    return _limitar(0.5 + (_contar(texto, POSITIVAS) - _contar(texto, NEGATIVAS)) * 0.15)


def churn_score(texto: str) -> float:
    return _limitar(_contar(texto, CHURN) * 0.3 + _contar(texto, NEGATIVAS) * 0.07)


def oportunidade_score(texto: str) -> float:
    return _limitar(_contar(texto, OPORTUNIDADE) * 0.3 + _contar(texto, POSITIVAS) * 0.05)


# Tópicos que viram "achados" (findings). Os tipos casam com o applies_to das
# estratégias cadastradas no back (technical_issue, integration_gap, ...).
TOPICOS = {
    "technical_issue": ("Problemas técnicos relatados",
                        ["lento", "lentidão", "erro", "falha", "travando", "bug", "instável", "instavel", "fora do ar"]),
    "integration_gap": ("Dificuldade de integração com outros sistemas",
                        ["integração", "integracao", "integrar", " api ", "legado"]),
    "pricing_concern": ("Insatisfação com preço ou custo",
                        ["caro", "preço", "preco", "desconto", "valor do plano", "mais barato", "custo alto"]),
    "usability_issue": ("Dificuldade de uso da plataforma",
                        ["difícil de usar", "dificil de usar", "dificuldade de uso", "dificuldade para usar",
                         "não sabe usar", "nao sabe usar", "complicado", "treinamento"]),
    "adoption_gap": ("Baixa adoção de funcionalidades",
                     ["baixa adoção", "baixa adocao", "não utiliza", "nao utiliza", "pouco uso",
                      "não usam", "nao usam"]),
}


def ocorrencias_topico(texto: str, tipo: str) -> int:
    return _contar(texto, TOPICOS[tipo][1])
