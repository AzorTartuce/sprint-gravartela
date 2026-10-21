package com.sprintjava.controller;

import com.sprintjava.controller.dto.AnaliseTranscricaoRequest;
import com.sprintjava.controller.dto.AnaliseTranscricaoResponse;
import com.sprintjava.exception.ApiException;
import com.sprintjava.service.AnaliseTranscricaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint consumido pela tela de análise de transcrições (frontend/frontsprint4). */
@RestController
@RequestMapping("/api/transcricoes")
public class TranscricaoController {

    private final AnaliseTranscricaoService service;

    public TranscricaoController(AnaliseTranscricaoService service) {
        this.service = service;
    }

    @PostMapping("/analisar")
    public AnaliseTranscricaoResponse analisar(@RequestBody AnaliseTranscricaoRequest request) {
        if (request.getTexto() == null || request.getTexto().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "A transcrição não pode ser vazia");
        }
        return service.analisar(request.getTexto());
    }
}
