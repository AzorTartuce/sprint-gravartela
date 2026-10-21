"""Extrai o texto de uma transcrição enviada como arquivo (.txt ou .json)."""
import json
from typing import Any, List

CHAVES_TEXTO = ("texto", "text", "mensagem", "message", "fala", "content", "conteudo", "conteúdo")
CHAVES_FALANTE = ("falante", "speaker", "autor", "author", "nome", "name", "orador")
CHAVES_LISTA = ("falas", "segmentos", "segments", "utterances", "mensagens", "messages",
                "transcricao", "transcrição", "transcript", "turns", "dialogos", "diálogos")


def _decodificar(dados: bytes) -> str:
    for codificacao in ("utf-8-sig", "latin-1"):
        try:
            return dados.decode(codificacao)
        except UnicodeDecodeError:
            continue
    return dados.decode("utf-8", errors="replace")


def _linhas(no: Any) -> List[str]:
    if isinstance(no, str):
        return [no.strip()] if no.strip() else []
    if isinstance(no, list):
        return [linha for item in no for linha in _linhas(item)]
    if isinstance(no, dict):
        texto = next((no[c] for c in CHAVES_TEXTO if isinstance(no.get(c), str)), None)
        if texto is not None:
            falante = next((no[c] for c in CHAVES_FALANTE if isinstance(no.get(c), str)), None)
            return [f"{falante}: {texto.strip()}" if falante else texto.strip()]
        for chave in CHAVES_LISTA:
            if chave in no:
                return _linhas(no[chave])
        # formato desconhecido: só strings com 3+ palavras, para ignorar ids e datas
        return [linha for v in no.values() for linha in _linhas(v) if len(linha.split()) >= 3]
    return []


def extrair_texto(dados: bytes, nome: str) -> str:
    bruto = _decodificar(dados)
    if (nome or "").lower().endswith(".json"):
        try:
            texto = "\n".join(_linhas(json.loads(bruto)))
        except json.JSONDecodeError as e:
            raise ValueError(f"JSON inválido: {e.msg}") from e
    else:
        texto = bruto.strip()
    if not texto.strip():
        raise ValueError("O arquivo não contém texto de reunião")
    return texto
