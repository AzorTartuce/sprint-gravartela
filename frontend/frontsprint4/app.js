(function () {
  const cfg = window.APP_CONFIG;
  const $ = (id) => document.getElementById(id);
  const history = [];

  const el = {
    texto: $("texto"), counter: $("counter"), btn: $("btnAnalisar"), clear: $("btnLimpar"),
    error: $("error"), empty: $("empty"), loading: $("loading"), result: $("result"),
    dropzone: $("dropzone"), fileInput: $("fileInput"),
    btnArquivo: $("btnArquivo"), fileName: $("fileName"),
    badge: $("modeBadge"), histBody: $("histBody"), histEmpty: $("histEmpty"),
  };

  el.badge.textContent = cfg.USE_MOCK ? "Modo demonstração" : "API: " + cfg.API_BASE_URL;
  el.badge.classList.add(cfg.USE_MOCK ? "mock" : "live");

  async function analisar(texto) {
    if (cfg.USE_MOCK) {
      await new Promise((r) => setTimeout(r, 600));
      return window.mockAnalisar(texto);
    }
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), cfg.TIMEOUT_MS);
    try {
      const resp = await fetch(cfg.API_BASE_URL + cfg.ANALYZE_PATH, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ texto: texto }),
        signal: ctrl.signal,
      });
      if (!resp.ok) {
        let detalhe = "";
        try { detalhe = (await resp.json()); detalhe = detalhe.mensagem || detalhe.detail || ""; } catch (_) {}
        throw new Error("A API respondeu " + resp.status + (detalhe ? ": " + detalhe : "."));
      }
      return await resp.json();
    } catch (e) {
      if (e.name === "AbortError") throw new Error("A API demorou demais para responder.");
      if (e instanceof TypeError) throw new Error("Não foi possível conectar à API em " + cfg.API_BASE_URL + ". Verifique se o back-end está no ar e se o CORS está liberado.");
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }

  const pct = (n) => Math.round((Number(n) || 0) * 100);
  const hora = () => new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  const norm = (s) => String(s || "").toUpperCase();

  function setBar(id, score, cls) {
    const b = $(id);
    b.style.width = pct(score) + "%";
    b.className = cls;
  }
  function setValue(id, text, cls) {
    const v = $(id);
    v.textContent = text;
    v.className = "value " + cls;
  }

  const SENT = { POSITIVO: ["Positivo", "good"], NEUTRO: ["Neutro", ""], NEGATIVO: ["Negativo", "bad"] };
  const CHURN = { BAIXO: ["Baixo", "good"], MEDIO: ["Médio", "warn"], ALTO: ["Alto", "bad"] };

  function render(r) {
    setValue("rProduto", r.produto || "Não identificado", "");
    $("rProdutoConf").textContent = r.produto && r.produtoConfianca != null ? "Confiança: " + pct(r.produtoConfianca) + "%" : "";

    const [sTxt, sCls] = SENT[norm(r.sentimento && r.sentimento.label)] || ["—", ""];
    setValue("rSentimento", sTxt, sCls);
    setBar("rSentimentoBar", r.sentimento && r.sentimento.score, sCls);
    $("rSentimentoScore").textContent = "Score: " + pct(r.sentimento && r.sentimento.score) + "%";

    const [cTxt, cCls] = CHURN[norm(r.churn && r.churn.nivel)] || ["—", ""];
    setValue("rChurn", cTxt, cCls);
    setBar("rChurnBar", r.churn && r.churn.score, cCls);
    $("rChurnScore").textContent = "Probabilidade: " + pct(r.churn && r.churn.score) + "%";

    const existe = !!(r.oportunidade && r.oportunidade.existe);
    setValue("rOport", existe ? "Identificada" : "Não identificada", existe ? "good" : "");
    setBar("rOportBar", r.oportunidade && r.oportunidade.score, existe ? "good" : "");
    $("rOportScore").textContent = "Probabilidade: " + pct(r.oportunidade && r.oportunidade.score) + "%";

    const ul = $("rTermos");
    ul.replaceChildren();
    (r.termos || []).forEach((t) => {
      const li = document.createElement("li");
      li.textContent = t;
      ul.appendChild(li);
    });
    if (!ul.children.length) {
      const li = document.createElement("li");
      li.textContent = "nenhum termo relevante";
      ul.appendChild(li);
    }

    $("rReco").textContent = r.recomendacao || "Sem recomendação disponível.";
  }

  function show(state) {
    el.empty.hidden = state !== "empty";
    el.loading.hidden = state !== "loading";
    el.result.hidden = state !== "result";
  }

  function showError(msg) {
    el.error.textContent = msg;
    el.error.hidden = !msg;
  }

  function renderHistory() {
    el.histBody.replaceChildren();
    el.histEmpty.hidden = history.length > 0;
    history.forEach((h, i) => {
      const tr = document.createElement("tr");
      const r = h.resultado;
      const cells = [
        h.hora,
        r.produto || "—",
        (SENT[norm(r.sentimento && r.sentimento.label)] || ["—"])[0],
        (CHURN[norm(r.churn && r.churn.nivel)] || ["—"])[0],
        r.oportunidade && r.oportunidade.existe ? "Sim" : "Não",
      ];
      cells.forEach((c) => {
        const td = document.createElement("td");
        td.textContent = c;
        tr.appendChild(td);
      });
      const td = document.createElement("td");
      const b = document.createElement("button");
      b.type = "button";
      b.textContent = "Ver";
      b.addEventListener("click", () => {
        el.texto.value = h.texto;
        updateCounter();
        render(h.resultado);
        show("result");
        window.scrollTo({ top: 0, behavior: "smooth" });
      });
      td.appendChild(b);
      tr.appendChild(td);
      el.histBody.appendChild(tr);
    });
  }

  function updateCounter() {
    el.counter.textContent = el.texto.value.length + " / " + el.texto.maxLength;
  }
  el.texto.addEventListener("input", updateCounter);

  const CAMPOS_TEXTO = ["texto", "text", "fala", "content", "conteudo", "conteúdo", "mensagem", "message", "transcript", "transcricao", "transcrição", "utterance"];
  const CAMPOS_FALANTE = ["falante", "speaker", "autor", "author", "participante", "orador", "nome", "name", "user", "usuario", "usuário"];
  const CAMPOS_LISTA = ["segmentos", "segments", "falas", "dialogos", "diálogos", "utterances", "mensagens", "messages", "turnos", "dialogo", "diálogo", "conversa", "reuniao", "reunião", "entries", "transcricao", "transcrição", "transcript"];
  const MAX_ARQUIVO = 5 * 1024 * 1024;

  const achar = (obj, chaves) => {
    const k = Object.keys(obj).find((c) => chaves.includes(c.toLowerCase()));
    return k === undefined ? undefined : obj[k];
  };
  const nomeDe = (v) => (typeof v === "string" ? v : v && typeof v === "object" ? achar(v, ["nome", "name"]) : undefined);

  
  function extrairTexto(d) {
    if (typeof d === "string") return d.trim();
    if (Array.isArray(d)) return d.map(extrairTexto).filter(Boolean).join("\n");
    if (d && typeof d === "object") {
      const fala = achar(d, CAMPOS_TEXTO);
      if (typeof fala === "string") {
        const quem = nomeDe(achar(d, CAMPOS_FALANTE));
        return (quem ? quem + ": " : "") + fala.trim();
      }
      const lista = achar(d, CAMPOS_LISTA);
      if (lista !== undefined) return extrairTexto(lista);
      
      return Object.values(d)
        .map((v) => (typeof v === "string" ? (v.trim().split(/\s+/).length >= 3 ? v.trim() : "") : extrairTexto(v)))
        .filter(Boolean)
        .join("\n");
    }
    return "";
  }

  async function carregarArquivo(file) {
    showError("");
    if (!file) return;
    if (!/\.json$/i.test(file.name)) return showError("Selecione um arquivo com extensão .json.");
    if (file.size > MAX_ARQUIVO) return showError("Arquivo maior que 5 MB.");
    let texto;
    try {
      texto = extrairTexto(JSON.parse(await file.text()));
    } catch (_) {
      return showError("O arquivo não é um JSON válido.");
    }
    if (texto.length < 20) return showError("Não encontrei texto de reunião nesse JSON.");
    if (texto.length > el.texto.maxLength) {
      texto = texto.slice(0, el.texto.maxLength);
      showError("Reunião muito longa: usei apenas os primeiros " + el.texto.maxLength + " caracteres.");
    }
    el.texto.value = texto;
    el.fileName.textContent = file.name;
    el.fileName.hidden = false;
    updateCounter();
    runAnalysis(true);
  }

  el.btnArquivo.addEventListener("click", () => el.fileInput.click());
  el.fileInput.addEventListener("change", () => {
    carregarArquivo(el.fileInput.files[0]);
    el.fileInput.value = "";
  });
  ["dragenter", "dragover"].forEach((ev) =>
    el.dropzone.addEventListener(ev, (e) => { e.preventDefault(); el.dropzone.classList.add("over"); })
  );
  ["dragleave", "drop"].forEach((ev) =>
    el.dropzone.addEventListener(ev, (e) => { e.preventDefault(); el.dropzone.classList.remove("over"); })
  );
  el.dropzone.addEventListener("drop", (e) => carregarArquivo(e.dataTransfer.files[0]));

  async function runAnalysis(keepError) {
    const texto = el.texto.value.trim();
    if (!keepError) showError("");
    showError("");
    if (texto.length < 20) {
      showError("Digite uma transcrição com pelo menos 20 caracteres.");
      return;
    }
    el.btn.disabled = true;
    show("loading");
    try {
      const resultado = await analisar(texto);
      render(resultado);
      show("result");
      history.unshift({ hora: hora(), texto: texto, resultado: resultado });
      renderHistory();
    } catch (e) {
      show("empty");
      showError(e.message);
    } finally {
      el.btn.disabled = false;
    }
  }
  el.btn.addEventListener("click", () => runAnalysis(false));

  el.clear.addEventListener("click", () => {
    el.texto.value = "";
    updateCounter();
    showError("");
    show("empty");
  });

  $("btnLimparHist").addEventListener("click", () => {
    history.length = 0;
    renderHistory();
  });

  document.querySelectorAll("[data-sample]").forEach((b) =>
    b.addEventListener("click", () => {
      el.texto.value = window.SAMPLES[b.dataset.sample];
      updateCounter();
      showError("");
    })
  );

  updateCounter();
  renderHistory();
  show("empty");
})();
