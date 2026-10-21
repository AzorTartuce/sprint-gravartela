"""Contrato JSON consumido pelo front (e repassado pelo back Java)."""
from typing import List, Optional

from pydantic import BaseModel, Field


class TranscricaoRequest(BaseModel):
    texto: str = Field(..., min_length=1)


class Sentimento(BaseModel):
    label: str  # POSITIVO | NEUTRO | NEGATIVO
    score: float


class Churn(BaseModel):
    nivel: str  # BAIXO | MEDIO | ALTO
    score: float


class Oportunidade(BaseModel):
    existe: bool
    score: float


class AnaliseResponse(BaseModel):
    produto: Optional[str]
    produtoConfianca: float
    sentimento: Sentimento
    churn: Churn
    oportunidade: Oportunidade
    termos: List[str]
    recomendacao: str
