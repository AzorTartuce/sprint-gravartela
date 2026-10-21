
import argparse
import json
import sys

from app.analisador import Analisador
from app.arquivo import extrair_texto
from app.relatorio import gerar_relatorio


def ler_texto(args) -> str:
    if args.arquivo:
        with open(args.arquivo, "rb") as f:
            return extrair_texto(f.read(), args.arquivo)
    if args.texto:
        return args.texto
    if not sys.stdin.isatty():
        texto = sys.stdin.read().strip()
        if texto:
            return texto
    raise ValueError("Informe o texto, --arquivo ou envie pela entrada padrão.")


def barra(score: float, largura: int = 20) -> str:
    cheio = round(max(0.0, min(1.0, score)) * largura)
    return "█" * cheio + "░" * (largura - cheio)


def imprimir(a, motores: dict) -> None:
    def linha(rotulo, valor, score, alvo):
        print(f"  {rotulo:<13} {valor:<14} {barra(score)} {score:.2f}   [{motores[alvo]}]")

    print("\nRESULTADO DA ANÁLISE")
    print("-" * 66)
    print(f"  {'Produto':<13} {a.produto or 'Não identificado':<14} "
          f"{barra(a.produtoConfianca)} {a.produtoConfianca:.2f}   [{motores['produto']}]")
    linha("Sentimento", a.sentimento.label, a.sentimento.score, "sentimento")
    linha("Risco churn", a.churn.nivel, a.churn.score, "churn")
    linha("Oportunidade", "SIM" if a.oportunidade.existe else "NÃO", a.oportunidade.score, "oportunidade")
    print(f"\n  Termos: {', '.join(a.termos) or '-'}")
    print(f"\n  Recomendação:\n    {a.recomendacao}")
    if "regras" in motores.values():
        print("\n  Obs.: indicadores marcados [regras] ainda não têm modelo treinado "
              "(ver treinar.py).")


def main() -> int:
    ap = argparse.ArgumentParser(description="Analisa uma transcrição de reunião.")
    ap.add_argument("texto", nargs="?", help="texto da transcrição")
    ap.add_argument("--arquivo", help="arquivo .txt ou .json com a transcrição")
    ap.add_argument("--json", action="store_true", help="imprime o resultado em JSON")
    ap.add_argument("--relatorio", action="store_true",
                    help="gera o relatório completo (achados, agentes, evidências)")
    args = ap.parse_args()

    try:
        texto = ler_texto(args)
    except (ValueError, OSError) as e:
        print(f"Erro: {e}", file=sys.stderr)
        return 2

    analisador = Analisador()
    if args.relatorio:
        saida = gerar_relatorio(texto, analisador)
        print(json.dumps(saida, ensure_ascii=False, indent=2))
        return 0

    resultado = analisador.analisar(texto)
    if args.json:
        print(json.dumps({**resultado.model_dump(), "motores": analisador.motores()},
                         ensure_ascii=False, indent=2))
    else:
        imprimir(resultado, analisador.motores())
    return 0


if __name__ == "__main__":
    sys.exit(main())
