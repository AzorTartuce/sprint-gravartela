"""Carrega os modelos treinados por treinar.py (models/modelo_<alvo>.joblib).

Cada alvo é independente: o que não tiver modelo cai no motor de regras.
"""
import re
from pathlib import Path
from typing import Dict, Optional, Tuple

import joblib

ALVOS = ("produto", "sentimento", "churn", "oportunidade")


class Modelos:
    def __init__(self, diretorio: Path):
        self.pipelines: Dict[str, object] = {}
        for alvo in ALVOS:
            arquivo = diretorio / f"modelo_{alvo}.joblib"
            if arquivo.exists():
                self.pipelines[alvo] = joblib.load(arquivo)

    def tem(self, alvo: str) -> bool:
        return alvo in self.pipelines

    def _proba(self, alvo: str, texto: str) -> Dict[str, float]:
        pipe = self.pipelines[alvo]
        probas = pipe.predict_proba([texto])[0]
        return {str(c): float(p) for c, p in zip(pipe.classes_, probas)}

    def produto(self, texto: str) -> Tuple[Optional[str], float]:
        proba = self._proba("produto", texto)
        classe, p = max(proba.items(), key=lambda kv: kv[1])
        return (None if classe.upper() == "NENHUM" else classe), p

    MAX_PALAVRAS_DIRETO = 60
    LIMIAR_TRECHO = 0.30

    def produto_agregado(self, texto: str) -> Tuple[Optional[str], float, Dict[str, float]]:
        """Texto longo: cada frase vota num produto. Devolve (principal, parcela de votos, parcela por produto)."""
        if len(texto.split()) <= self.MAX_PALAVRAS_DIRETO:
            nome, p = self.produto(texto)
            return nome, p, ({nome: 1.0} if nome else {})
        trechos = [s.strip() for s in re.split(r"(?<=[.!?])\s+", texto) if len(s.split()) >= 3]
        pipe = self.pipelines["produto"]
        votos: Dict[str, float] = {}
        for probas in pipe.predict_proba(trechos):
            pr = {str(c): float(v) for c, v in zip(pipe.classes_, probas)}
            nenhum = pr.pop("NENHUM", 0.0)
            classe, v = max(pr.items(), key=lambda kv: kv[1])
            if v >= self.LIMIAR_TRECHO and v > nenhum:
                votos[classe] = votos.get(classe, 0.0) + 1
        total = sum(votos.values())
        if not total:
            return None, 0.0, {}
        partes = {k: v / total for k, v in sorted(votos.items(), key=lambda kv: -kv[1])}
        principal = next(iter(partes))
        return principal, partes[principal], partes

    def sentimento(self, texto: str) -> Tuple[str, float]:
        proba = self._proba("sentimento", texto)
        label = max(proba.items(), key=lambda kv: kv[1])[0]
        score = proba.get("POSITIVO", 0.0) + 0.5 * proba.get("NEUTRO", 0.0)
        return label, score

    def binario(self, alvo: str, texto: str) -> float:
        """Probabilidade da classe positiva ("1") de churn/oportunidade."""
        return self._proba(alvo, texto).get("1", 0.0)
