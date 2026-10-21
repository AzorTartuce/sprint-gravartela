
(function () {
  const PRODUTOS = {
    Protheus: ["protheus"],
    RM: [" rm ", "totvs rm", "folha de pagamento", "rh "],
    Fluig: ["fluig"],
    Datasul: ["datasul"],
    Winthor: ["winthor"],
  };
  const POSITIVAS = ["ótimo", "otimo", "excelente", "satisfeito", "feliz", "gostei", "parabéns", "resolveu", "funciona bem", "obrigado", "ajudou"];
  const NEGATIVAS = ["lento", "lentidão", "problema", "erro", "falha", "insatisfeito", "ruim", "péssimo", "pessimo", "demora", "travando", "reclamação", "reclamacao", "caro", "cancelar"];
  const CHURN = ["cancelar", "cancelamento", "concorrente", "outro fornecedor", "migrar", "trocar de sistema", "rescindir", "não vamos renovar", "nao vamos renovar", "insatisfeito"];
  const OPORT = ["orçamento", "orcamento", "proposta", "contratar", "novo módulo", "novo modulo", "expandir", "ampliar", "interesse", "licenças adicionais", "licencas adicionais", "upgrade", "filial"];
  const STOP = new Set("a o as os um uma de da do das dos em no na nos nas e é que para por com se mas ao à já foi ser tem têm não nao mais muito como sobre isso essa esse está estao estamos vamos pode podem cliente falou disse então entao também tambem pra pro".split(" "));

  const clamp = (n) => Math.max(0, Math.min(1, n));
  const count = (txt, list) => list.reduce((n, k) => n + (txt.includes(k) ? 1 : 0), 0);

  window.mockAnalisar = function (texto) {
    const t = " " + texto.toLowerCase() + " ";

    let produto = null, melhor = 0;
    for (const [nome, chaves] of Object.entries(PRODUTOS)) {
      const c = count(t, chaves);
      if (c > melhor) { melhor = c; produto = nome; }
    }

    const pos = count(t, POSITIVAS), neg = count(t, NEGATIVAS);
    const sentScore = clamp(0.5 + (pos - neg) * 0.15);
    const sentLabel = sentScore > 0.6 ? "POSITIVO" : sentScore < 0.4 ? "NEGATIVO" : "NEUTRO";

    const churnScore = clamp(count(t, CHURN) * 0.3 + neg * 0.07);
    const churnNivel = churnScore >= 0.6 ? "ALTO" : churnScore >= 0.3 ? "MEDIO" : "BAIXO";

    const oportScore = clamp(count(t, OPORT) * 0.3 + pos * 0.05);
    const oportExiste = oportScore >= 0.3;

    const freq = {};
    (t.match(/[a-zà-ú]{4,}/g) || []).forEach((w) => {
      if (!STOP.has(w)) freq[w] = (freq[w] || 0) + 1;
    });
    const termos = Object.entries(freq).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([w]) => w);

    let recomendacao;
    if (churnNivel === "ALTO") {
      recomendacao = "Acionar o gestor de contas em até 24h, abrir plano de retenção e priorizar a resolução dos problemas relatados.";
    } else if (oportExiste) {
      recomendacao = "Encaminhar ao time comercial para enviar proposta e agendar demonstração do módulo de interesse.";
    } else if (churnNivel === "MEDIO") {
      recomendacao = "Agendar contato de acompanhamento com o suporte para confirmar que os pontos levantados foram resolvidos.";
    } else {
      recomendacao = "Manter o relacionamento regular e registrar a interação no CRM.";
    }

    return {
      produto: produto,
      produtoConfianca: produto ? clamp(0.55 + melhor * 0.15) : 0,
      sentimento: { label: sentLabel, score: Number(sentScore.toFixed(2)) },
      churn: { nivel: churnNivel, score: Number(churnScore.toFixed(2)) },
      oportunidade: { existe: oportExiste, score: Number(oportScore.toFixed(2)) },
      termos: termos,
      recomendacao: recomendacao,
    };
  };

  window.SAMPLES = {
    churn: "O cliente ligou muito insatisfeito. O Protheus está com lentidão há semanas, o erro de fechamento fiscal continua e a demora do suporte é um problema sério. Disse que está avaliando migrar para um concorrente e que talvez nao vamos renovar o contrato.",
    oportunidade: "Reunião excelente com o financeiro. Estão satisfeitos com o Fluig e demonstraram interesse em contratar um novo módulo para expandir o uso nas filiais. Pediram uma proposta e orçamento até o fim do mês.",
    neutro: "Contato de rotina para confirmar a data da próxima atualização do RM. O cliente informou que a equipe de RH está treinando os novos usuários e que retorna se houver dúvidas.",
  };
})();
