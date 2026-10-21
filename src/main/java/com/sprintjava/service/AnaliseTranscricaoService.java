package com.sprintjava.service;

import com.sprintjava.controller.dto.AnaliseTranscricaoResponse;
import com.sprintjava.controller.dto.AnaliseTranscricaoResponse.Churn;
import com.sprintjava.controller.dto.AnaliseTranscricaoResponse.Oportunidade;
import com.sprintjava.controller.dto.AnaliseTranscricaoResponse.Sentimento;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintjava.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
public class AnaliseTranscricaoService {

    private static final Map<String, List<String>> PRODUTOS = new LinkedHashMap<>();

    static {
        PRODUTOS.put("Protheus", List.of("protheus"));
        PRODUTOS.put("RM", List.of(" rm ", "totvs rm", "folha de pagamento", "rh "));
        PRODUTOS.put("Fluig", List.of("fluig"));
        PRODUTOS.put("Datasul", List.of("datasul"));
        PRODUTOS.put("Winthor", List.of("winthor"));
    }

    private static final List<String> POSITIVAS = List.of("ótimo", "otimo", "excelente", "satisfeito", "feliz",
            "gostei", "parabéns", "resolveu", "funciona bem", "obrigado", "ajudou");
    private static final List<String> NEGATIVAS = List.of("lento", "lentidão", "problema", "erro", "falha",
            "insatisfeito", "ruim", "péssimo", "pessimo", "demora", "travando", "reclamação", "reclamacao",
            "caro", "cancelar");
    private static final List<String> CHURN = List.of("cancelar", "cancelamento", "concorrente",
            "outro fornecedor", "migrar", "trocar de sistema", "rescindir", "não vamos renovar",
            "nao vamos renovar", "insatisfeito");
    private static final List<String> OPORTUNIDADE = List.of("orçamento", "orcamento", "proposta", "contratar",
            "novo módulo", "novo modulo", "expandir", "ampliar", "interesse", "licenças adicionais",
            "licencas adicionais", "upgrade", "filial");
    private static final Set<String> STOPWORDS = Set.of("a", "o", "as", "os", "um", "uma", "de", "da", "do",
            "das", "dos", "em", "no", "na", "nos", "nas", "e", "é", "que", "para", "por", "com", "se", "mas",
            "ao", "à", "já", "foi", "ser", "tem", "têm", "não", "nao", "mais", "muito", "como", "sobre",
            "isso", "essa", "esse", "está", "estao", "estamos", "vamos", "pode", "podem", "cliente", "falou",
            "disse", "então", "entao", "também", "tambem", "pra", "pro");
    private static final Pattern PALAVRA = Pattern.compile("[a-zà-ú]{4,}");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String pythonUrl;

    public AnaliseTranscricaoService(@Value("${app.python.transcricao-url:}") String pythonUrl) {
        this.pythonUrl = pythonUrl == null ? "" : pythonUrl.trim();
    }

    public AnaliseTranscricaoResponse analisar(String texto) {
        return pythonUrl.isEmpty() ? analisarLocal(texto) : analisarComPython(texto);
    }

    private AnaliseTranscricaoResponse analisarComPython(String texto) {
        try {
            String corpo = objectMapper.writeValueAsString(Map.of("texto", texto));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Serviço de análise (Python) respondeu " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), AnaliseTranscricaoResponse.class);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
                    "Tempo limite excedido ao aguardar o serviço de análise (Python)", e);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Serviço de análise (Python) indisponível em " + pythonUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Chamada ao serviço de análise interrompida", e);
        }
    }

    private AnaliseTranscricaoResponse analisarLocal(String texto) {
        String t = " " + texto.toLowerCase() + " ";

        String produto = null;
        int melhor = 0;
        for (Map.Entry<String, List<String>> e : PRODUTOS.entrySet()) {
            int c = contar(t, e.getValue());
            if (c > melhor) {
                melhor = c;
                produto = e.getKey();
            }
        }

        int pos = contar(t, POSITIVAS);
        int neg = contar(t, NEGATIVAS);
        double sentScore = limitar(0.5 + (pos - neg) * 0.15);
        String sentLabel = sentScore > 0.6 ? "POSITIVO" : sentScore < 0.4 ? "NEGATIVO" : "NEUTRO";

        double churnScore = limitar(contar(t, CHURN) * 0.3 + neg * 0.07);
        String churnNivel = churnScore >= 0.6 ? "ALTO" : churnScore >= 0.3 ? "MEDIO" : "BAIXO";

        double oportScore = limitar(contar(t, OPORTUNIDADE) * 0.3 + pos * 0.05);
        boolean oportExiste = oportScore >= 0.3;

        return new AnaliseTranscricaoResponse(
                produto,
                produto != null ? arredondar(limitar(0.55 + melhor * 0.15)) : 0,
                new Sentimento(sentLabel, arredondar(sentScore)),
                new Churn(churnNivel, arredondar(churnScore)),
                new Oportunidade(oportExiste, arredondar(oportScore)),
                termosPrincipais(t),
                recomendar(churnNivel, oportExiste));
    }

    private String recomendar(String churnNivel, boolean oportunidade) {
        if ("ALTO".equals(churnNivel)) {
            return "Acionar o gestor de contas em até 24h, abrir plano de retenção e priorizar a resolução dos problemas relatados.";
        }
        if (oportunidade) {
            return "Encaminhar ao time comercial para enviar proposta e agendar demonstração do módulo de interesse.";
        }
        if ("MEDIO".equals(churnNivel)) {
            return "Agendar contato de acompanhamento com o suporte para confirmar que os pontos levantados foram resolvidos.";
        }
        return "Manter o relacionamento regular e registrar a interação no CRM.";
    }

    private List<String> termosPrincipais(String texto) {
        Map<String, Integer> frequencia = new LinkedHashMap<>();
        Matcher m = PALAVRA.matcher(texto);
        while (m.find()) {
            String palavra = m.group();
            if (!STOPWORDS.contains(palavra)) {
                frequencia.merge(palavra, 1, Integer::sum);
            }
        }
        return frequencia.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int contar(String texto, List<String> chaves) {
        int n = 0;
        for (String chave : chaves) {
            if (texto.contains(chave)) {
                n++;
            }
        }
        return n;
    }

    private double limitar(double valor) {
        return Math.max(0, Math.min(1, valor));
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100) / 100.0;
    }
}
