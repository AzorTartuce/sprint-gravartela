# python-analise

Serviço FastAPI que analisa transcrições de reuniões e devolve o JSON que o front
(`frontend/frontsprint4`) exibe. O back Java chama este serviço por HTTP.

```
Front --POST /api/transcricoes/analisar--> Java (8000) --POST /analisar-transcricao--> Python (5001)
```

## Estado atual
Não há dataset nem modelo treinado no repositório. Até o treino, cada indicador usa
**regras por palavras-chave** (`app/regras.py`), que não são machine learning.
`GET /saude` mostra, por indicador, se o motor ativo é `modelo` ou `regras`.

## Rodar
```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --port 5001      # docs interativas em /docs
.venv/bin/pytest                                  # testes
```
Use a porta **5001**: no macOS a 5000 é ocupada pelo AirPlay Receiver.

Para o Java usar este serviço, defina a variável de ambiente no back:
`PYTHON_TRANSCRICAO_URL=http://localhost:5001/analisar-transcricao`
(em Docker, `http://host.docker.internal:5001/analisar-transcricao`).
Vazia, o Java usa a análise provisória interna. Configurada e com o Python fora
do ar, o Java responde 502 (não esconde a falha).

## Demonstração por linha de comando
Roda o pipeline completo sem subir a API (usa os modelos de `models/`, e as regras nos
indicadores que não tiverem modelo; a saída mostra `[modelo]` ou `[regras]` em cada um):
```bash
.venv/bin/python analisar.py "O cliente está insatisfeito com o Protheus e quer cancelar."
.venv/bin/python analisar.py --arquivo reuniao.json          # .txt ou .json
.venv/bin/python analisar.py "texto" --json                  # saída em JSON
.venv/bin/python analisar.py --arquivo reuniao.txt --relatorio   # achados, agentes e evidências
echo "texto" | .venv/bin/python analisar.py                  # entrada padrão
```

## Treinar com dados reais
1. Monte `data/transcricoes.csv` (UTF-8) com as colunas:

| coluna | valores |
|---|---|
| `texto` | trecho da transcrição |
| `produto` | Protheus, RM, Fluig... ou `NENHUM` |
| `sentimento` | `POSITIVO`, `NEUTRO`, `NEGATIVO` |
| `churn` | `1` (risco) ou `0` |
| `oportunidade` | `1` ou `0` |

   Qualquer coluna-alvo pode faltar: só as presentes são treinadas. São necessários pelo
   menos 3 exemplos por classe.
2. `.venv/bin/python treinar.py data/transcricoes.csv`
3. O script compara LogisticRegression, ComplementNB e RandomForest (TF-IDF com
   unigramas e bigramas) por validação cruzada com F1 macro, avalia o vencedor em um
   conjunto de teste separado (20%) e grava `models/modelo_<alvo>.joblib` e
   `models/metricas.json`.
4. Reinicie o serviço. `/saude` passa a mostrar `modelo` nos alvos treinados.

`models/metricas.json` traz a comparação e o relatório de teste, que servem de base para a
justificativa do modelo escolhido na documentação.

## Contrato
`POST /analisar-transcricao` com `{"texto": "..."}` devolve `produto`, `produtoConfianca`,
`sentimento{label,score}`, `churn{nivel,score}`, `oportunidade{existe,score}`, `termos`,
`recomendacao`. A recomendação é derivada por regras de negócio a partir dos indicadores.
