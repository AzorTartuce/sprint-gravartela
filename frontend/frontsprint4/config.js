// Configuração do front-end. Altere aqui para apontar para a API Java.
window.APP_CONFIG = {
  // URL base da API Java (Spring Boot, Quarkus, etc.)
  API_BASE_URL: "http://localhost:8000",

  // Endpoint que recebe a transcrição e devolve a análise (POST)
  ANALYZE_PATH: "/api/transcricoes/analisar",

  // true  = usa análise simulada no navegador (não chama a API)
  // false = chama a API Java; se falhar, mostra o erro (sem simular)
  USE_MOCK: false,

  // Tempo máximo de espera pela API
  TIMEOUT_MS: 15000,
};
