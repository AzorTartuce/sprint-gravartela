package com.sprintjava.controller.dto;

/** Corpo recebido pelo front: {"texto": "..."}. */
public class AnaliseTranscricaoRequest {

    private String texto;

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }
}
