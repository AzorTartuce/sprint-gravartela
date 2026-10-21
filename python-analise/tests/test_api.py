import subprocess
import sys
from pathlib import Path

import pandas as pd
import pytest
from fastapi.testclient import TestClient

from app.analisador import Analisador
from app.main import app

RAIZ = Path(__file__).resolve().parent.parent
client = TestClient(app)


@pytest.fixture(autouse=True)
def so_regras(monkeypatch, tmp_path):
    """Os testes de API validam o contrato com o motor de regras, independentemente
    dos modelos que existam em models/ (que mudam a cada treino)."""
    import app.main as main
    monkeypatch.setattr(main, "analisador", Analisador(tmp_path / "sem_modelos"))


def test_regras_churn():
    r = client.post("/analisar-transcricao", json={
        "texto": "Cliente insatisfeito com lentidão no Protheus, avaliando migrar para um concorrente."})
    assert r.status_code == 200
    corpo = r.json()
    assert corpo["produto"] == "Protheus"
    assert corpo["churn"]["nivel"] == "ALTO"
    assert corpo["sentimento"]["label"] == "NEGATIVO"
    assert set(corpo) == {"produto", "produtoConfianca", "sentimento", "churn",
                          "oportunidade", "termos", "recomendacao"}


def test_texto_vazio_rejeitado():
    assert client.post("/analisar-transcricao", json={"texto": ""}).status_code == 422


def test_saude_informa_motor():
    assert set(client.get("/saude").json()["motores"]) == {"produto", "sentimento", "churn", "oportunidade"}


def test_treino_e_uso_do_modelo(tmp_path):
    """Valida o fluxo treinar.py -> models/ -> Analisador. Os dados abaixo são
    sintéticos e existem só neste teste; não representam o dataset real."""
    pos = ["excelente atendimento muito satisfeito", "ótimo resultado gostei bastante", "funciona bem obrigado ajudou"]
    neg = ["péssimo sistema insatisfeito com erro", "lentidão e falha constante ruim", "problema grave quero cancelar"]
    neu = ["reunião de rotina sobre atualização", "confirmar data da próxima versão", "contato para alinhar agenda"]
    linhas = []
    for i in range(4):
        for texto, s, c in [(p, "POSITIVO", 0) for p in pos] + [(n, "NEGATIVO", 1) for n in neg] + [(u, "NEUTRO", 0) for u in neu]:
            linhas.append({"texto": f"{texto} {i}", "sentimento": s, "churn": c})
    csv = tmp_path / "dados.csv"
    pd.DataFrame(linhas).to_csv(csv, index=False)
    saida = tmp_path / "models"

    subprocess.run([sys.executable, str(RAIZ / "treinar.py"), str(csv), "--saida", str(saida)],
                   check=True, cwd=RAIZ)
    assert (saida / "modelo_sentimento.joblib").exists()
    assert (saida / "metricas.json").exists()

    analisador = Analisador(saida)
    assert analisador.motores()["sentimento"] == "modelo"
    assert analisador.motores()["produto"] == "regras"
    resp = analisador.analisar("péssimo sistema com erro e falha, quero cancelar")
    assert resp.sentimento.label == "NEGATIVO"
    assert resp.churn.score > 0.5


REUNIAO = {"falas": [
    {"falante": "Cliente", "texto": "O Protheus está com lentidão e erro no fechamento fiscal há semanas."},
    {"falante": "Cliente", "texto": "Estamos insatisfeitos e avaliando migrar para um concorrente."},
    {"falante": "Consultor", "texto": "Vamos escalar com o suporte técnico e revisar o desconto do plano."},
]}


def test_upload_json_gera_relatorio_com_achados():
    import json
    r = client.post("/analisar", content=json.dumps(REUNIAO).encode(),
                    headers={"X-File-Name": "reuniao.json", "Content-Type": "application/octet-stream"})
    assert r.status_code == 200
    corpo = r.json()
    assert set(corpo) == {"triage", "selected_agents", "reports", "final_report"}
    tipos = {f["finding_type"] for f in corpo["final_report"]["findings"]}
    assert {"technical_issue", "churn_risk", "pricing_concern"} <= tipos
    assert corpo["final_report"]["produto"] == "Protheus"
    assert "Cliente: O Protheus" in corpo["reports"][0]["evidencias"][0]
    assert {"sentimento", "churn", "suporte"} <= set(corpo["selected_agents"])


def test_upload_txt_e_arquivo_vazio():
    ok = client.post("/analisar", content="Reunião de rotina sobre o Fluig, tudo certo.".encode(),
                     headers={"X-File-Name": "r.txt"})
    assert ok.status_code == 200 and ok.json()["final_report"]["findings"] == []
    assert client.post("/analisar", content=b"   ", headers={"X-File-Name": "r.txt"}).status_code == 422
    assert client.post("/analisar", content=b"{nao e json", headers={"X-File-Name": "r.json"}).status_code == 422
