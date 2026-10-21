# Protótipo funcional – Análise de Transcrições (Sprint 4 • Data Science)

Interface web (HTML/CSS/JS puro, sem build) que recebe uma transcrição e mostra:
produto, sentimento, risco de churn, oportunidade comercial, principais termos e recomendação.

## Como rodar
Sirva a pasta por HTTP (`python3 -m http.server 5500` e acesse `http://localhost:5500`) ou use o `docker compose up` da raiz (front em `http://localhost:5500`). Não abra o `index.html` com duplo clique: o CORS não funciona com `file://`.

Por padrão chama a API Java (`USE_MOCK: false` em `config.js`, `API_BASE_URL` = `http://localhost:8000`). Com `USE_MOCK: true` roda em modo demonstração, com análise simulada no navegador.

## Importar reunião em .json
Botão **Carregar reunião (.json)** ou arrastar o arquivo para a tela. O front extrai o texto,
mostra na caixa e já dispara a análise. Formatos aceitos:
- string simples ou lista de strings;
- lista de falas `{ "falante": "...", "texto": "..." }` (também `speaker`/`text`, `autor`/`mensagem` etc.);
- objeto com a lista dentro de `falas`, `segmentos`, `utterances`, `mensagens`, `transcricao`...;
- formato desconhecido: junta os textos com 3+ palavras.

O back-end continua recebendo só `{ "texto": "..." }`. Exemplo para testar: `exemplo-reuniao.json`.

## Conectar com o back-end Java
1. Em `config.js`: confira `USE_MOCK: false` e `API_BASE_URL` / `ANALYZE_PATH`.
2. O back libera CORS para `localhost`/`127.0.0.1` em qualquer porta; sirva a página por HTTP (não use `file://`).
3. O contrato abaixo já está implementado no back.

### Contrato
`POST {API_BASE_URL}/api/transcricoes/analisar`

Requisição:
```json
{ "texto": "texto da transcrição..." }
```

Resposta `200 OK`:
```json
{
  "produto": "Protheus",
  "produtoConfianca": 0.87,
  "sentimento": { "label": "NEGATIVO", "score": 0.22 },
  "churn": { "nivel": "ALTO", "score": 0.81 },
  "oportunidade": { "existe": false, "score": 0.10 },
  "termos": ["lentidão", "suporte", "contrato"],
  "recomendacao": "Acionar o gestor de contas em até 24h..."
}
```

| Campo | Valores |
|---|---|
| `produto` | texto ou `null` se não identificado |
| `sentimento.label` | `POSITIVO`, `NEUTRO`, `NEGATIVO` |
| `churn.nivel` | `BAIXO`, `MEDIO`, `ALTO` |
| `*.score`, `produtoConfianca` | número de 0 a 1 |
| `termos` | lista de strings |

Erros: qualquer status != 2xx; se vier `{ "detail": "..." }` (ou `{ "mensagem": "..." }`) o texto é exibido na tela
(o back devolve `422` para texto vazio, `400` para JSON malformado e `502/504` se o serviço Python falhar).

Tabela para o PDF do DDD (endpoints):

| URI | Verbo | Status |
|---|---|---|
| `/api/transcricoes/analisar` | POST | 200, 400, 422, 502, 504 |

## Arquivos
- `index.html` – estrutura da tela
- `style.css` – estilo (paleta TOTVS)
- `config.js` – URL da API e modo demo
- `app.js` – chamada à API e renderização
- `mock.js` – análise simulada (apenas para demonstração)
