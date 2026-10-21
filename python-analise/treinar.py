"""Treina os modelos de análise a partir de um CSV rotulado.

Uso:  python treinar.py data/transcricoes.csv [--saida models]

Aceita CSV ou JSON (lista de objetos). Colunas (UTF-8): texto, produto, sentimento, churn, oportunidade.
Cada coluna-alvo é opcional; só os alvos presentes no arquivo são treinados.
  produto      -> nome do produto (Protheus, RM, Fluig...) ou NENHUM
  sentimento   -> POSITIVO | NEUTRO | NEGATIVO
  churn        -> 1 (há risco) | 0
  oportunidade -> 1 (há oportunidade comercial) | 0

Para cada alvo: compara 3 candidatos por validação cruzada (F1 macro) no
conjunto de treino, avalia o vencedor em um conjunto de teste separado,
reajusta com todos os dados e grava models/modelo_<alvo>.joblib e
models/metricas.json (usado para justificar o modelo na documentação).
"""
import argparse
import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, f1_score
from sklearn.model_selection import (RepeatedStratifiedKFold, StratifiedKFold, cross_val_predict,
                                     cross_val_score, train_test_split)
from sklearn.naive_bayes import ComplementNB
from sklearn.pipeline import Pipeline

ALVOS = ("produto", "sentimento", "churn", "oportunidade")
BINARIOS = ("churn", "oportunidade")
SIM = {"1", "sim", "true", "s", "yes"}
LIMITE_DADOS_PEQUENOS = 60


def candidatos():
    return {
        "LogisticRegression": LogisticRegression(max_iter=1000, class_weight="balanced"),
        "ComplementNB": ComplementNB(),
        "RandomForest": RandomForestClassifier(n_estimators=200, class_weight="balanced", random_state=42),
    }


def pipeline(clf):
    return Pipeline([
        ("tfidf", TfidfVectorizer(lowercase=True, ngram_range=(1, 2), min_df=1, sublinear_tf=True)),
        ("clf", clf),
    ])


def normalizar(alvo, serie):
    s = serie.astype(str).str.strip()
    if alvo in BINARIOS:
        return s.str.lower().map(lambda v: "1" if v in SIM else "0")
    if alvo == "sentimento":
        return s.str.upper()
    return s


def treinar_alvo(alvo, textos, y, saida):
    contagem = y.value_counts()
    if len(contagem) < 2:
        return {"erro": "o alvo precisa de pelo menos 2 classes"}
    if contagem.min() < 3:
        return {"erro": f"classe com poucos exemplos ({contagem.min()}); são necessários >= 3 por classe"}

    pequeno = len(y) < LIMITE_DADOS_PEQUENOS
    if pequeno:
        # Poucos dados: um conjunto de teste separado teria só alguns exemplos e
        # a nota seria ruído. Usa validação cruzada repetida sobre tudo.
        splits = max(2, min(5, int(contagem.min())))
        cv = RepeatedStratifiedKFold(n_splits=splits, n_repeats=10, random_state=42)
        X_tr, y_tr = textos, y
    else:
        X_tr, X_te, y_tr, y_te = train_test_split(textos, y, test_size=0.2, stratify=y, random_state=42)
        splits = max(2, min(5, int(y_tr.value_counts().min())))
        cv = StratifiedKFold(n_splits=splits, shuffle=True, random_state=42)

    comparacao = {}
    for nome, clf in candidatos().items():
        notas = cross_val_score(pipeline(clf), X_tr, y_tr, cv=cv, scoring="f1_macro")
        comparacao[nome] = {"f1_macro_cv_media": round(float(notas.mean()), 4),
                            "f1_macro_cv_desvio": round(float(notas.std()), 4)}
    vencedor = max(comparacao, key=lambda n: comparacao[n]["f1_macro_cv_media"])

    if pequeno:
        pred = cross_val_predict(pipeline(candidatos()[vencedor]), textos, y,
                                 cv=StratifiedKFold(n_splits=splits, shuffle=True, random_state=42))
        y_te = y
    else:
        pred = pipeline(candidatos()[vencedor]).fit(X_tr, y_tr).predict(X_te)
    relatorio = classification_report(y_te, pred, output_dict=True, zero_division=0)

    final = pipeline(candidatos()[vencedor]).fit(textos, y)
    joblib.dump(final, saida / f"modelo_{alvo}.joblib")

    return {
        "exemplos": int(len(y)),
        "classes": {k: int(v) for k, v in contagem.items()},
        "comparacao_validacao_cruzada": comparacao,
        "modelo_escolhido": vencedor,
        "avaliacao": "validação cruzada (poucos dados)" if pequeno else "conjunto de teste (20%)",
        "f1_macro_teste": round(float(f1_score(y_te, pred, average="macro")), 4),
        "relatorio_teste": relatorio,
    }


def carregar(caminho):
    """Lê CSV ou JSON (lista de objetos), detectando pelo conteúdo e não pela extensão."""
    with open(caminho, encoding="utf-8-sig") as f:
        inicio = f.read(1).lstrip()
    if inicio in ("[", "{"):
        return pd.read_json(caminho, encoding="utf-8")
    return pd.read_csv(caminho, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--saida", default="models")
    args = ap.parse_args()

    df = carregar(args.csv).dropna(subset=["texto"]).reset_index(drop=True)
    textos = (" " + df["texto"].astype(str).str.lower() + " ")
    saida = Path(args.saida)
    saida.mkdir(parents=True, exist_ok=True)

    metricas = {}
    for alvo in ALVOS:
        if alvo not in df.columns:
            continue
        sub = df.dropna(subset=[alvo])
        metricas[alvo] = treinar_alvo(alvo, textos.loc[sub.index], normalizar(alvo, sub[alvo]), saida)
        print(alvo, "->", metricas[alvo].get("modelo_escolhido", metricas[alvo].get("erro")),
              metricas[alvo].get("f1_macro_teste", ""))

    (saida / "metricas.json").write_text(json.dumps(metricas, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
