from typing import Optional
from urllib.parse import unquote

from fastapi import FastAPI, HTTPException, Request

from .analisador import Analisador
from .arquivo import extrair_texto
from .relatorio import gerar_relatorio
from .schemas import AnaliseResponse, TranscricaoRequest

app = FastAPI(title="Insight Hive - Análise de Transcrições")
analisador = Analisador()


@app.get("/saude")
def saude():
    """Informa, por indicador, se a análise usa modelo treinado ou regras provisórias."""
    return {"status": "ok", "motores": analisador.motores()}


@app.post("/analisar-transcricao", response_model=AnaliseResponse)
def analisar_transcricao(req: TranscricaoRequest):
    texto = req.texto
    if texto.lstrip().startswith(("{", "[")):
        try:
            texto = extrair_texto(texto.encode("utf-8"), "transcricao.json")
        except ValueError:
            pass  # não era JSON de reunião: analisa o texto como veio
    return analisador.analisar(texto)


@app.post("/analisar")
async def analisar_arquivo(request: Request):
    """Upload de reunião feito pelo back Java: corpo = bytes do arquivo (.txt/.json),
    nome em X-File-Name. Devolve triage, selected_agents, reports e final_report."""
    dados = await request.body()
    nome: Optional[str] = unquote(request.headers.get("x-file-name", ""))
    try:
        texto = extrair_texto(dados, nome)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    return gerar_relatorio(texto, analisador)
