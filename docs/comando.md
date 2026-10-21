Pelo que está no disco, o treinamento não deu certo, ou nunca terminou. Também não vi o projeto rodando no Docker.

Treinamento do Python

- python-analise/models/ só tem o .gitkeep. Não existem modelo_*.joblib nem metricas.json, que o treinar.py grava ao terminar.
- O data/transcricoes.csv existe (30 KB) e o treinar.py foi editado às 18:48, então os dados estão prontos.
- Enquanto não houver modelo, o serviço usa as regras por palavras-chave, que não são machine learning.

Para treinar:
cd ~/Desktop/sprintjavaOficial/python-analise
.venv/bin/python treinar.py data/transcricoes.csv
ls models/                 # deve listar modelo_*.joblib e metricas.json
cat models/metricas.json   # F1 de cada modelo
O script imprime uma linha por alvo com o modarecer erro, falta exemplo de alguma classe (omínimo é 3 por classe).

Depois de treinar, refaça o build. O Dockerfile copia models/ para dentro da imagem, então um container já construído
não enxerga o modelo novo.

Subir e testar o projeto

O único container ativo é festive_blackwell, VS Code. Ele não é o compose do projeto. Naraiz:

cd ~/Desktop/sprintjavaOficial
docker compose up --build -d
docker compose ps                     # os 3 serviços devem estar "Up" (python-analise "healthy")
docker compose logs -f backend        # acomp

Testes:
# Python: confirma se cada indicador usa "modelo" ou "regras"
curl http://localhost:5001/saude

# Python direto
curl -X POST http://localhost:5001/analisar-transcricao \
  -H "Content-Type: application/json" \
  -d '{"texto":"O cliente do Protheus está insatisfeito e ameaça cancelar o contrato"}'

# Java (porta 8000), com o Python por trás
curl -i http://localhost:8000/api/clients     o Java subiu e pede login
O front fica em http://localhost:5500. Os testes unitários do Python são cd python-analise && .venv/bin/pytest.

Como saber se o treino funcionou: em /saude, os alvos treinados devem aparecer como modelo. Se ainda aparecer regras,
o modelo não foi carregado.

Quer que eu rode o treino agora e te diga as