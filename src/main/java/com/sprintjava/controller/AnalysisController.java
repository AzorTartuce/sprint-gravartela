package com.sprintjava.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintjava.controller.dto.AnalysisJobResponse;
import com.sprintjava.controller.dto.MeetingDetailResponse;
import com.sprintjava.dao.AnalysisJobDAO;
import com.sprintjava.dao.ClientDAO;
import com.sprintjava.dao.FindingDAO;
import com.sprintjava.dao.MeetingDAO;
import com.sprintjava.exception.ApiException;
import com.sprintjava.model.AnalysisJob;
import com.sprintjava.model.Client;
import com.sprintjava.model.Finding;
import com.sprintjava.model.Meeting;
import com.sprintjava.model.User;
import com.sprintjava.service.AnalisePythonException;
import com.sprintjava.service.FindingsDaAnalise;
import com.sprintjava.service.ServicoAnalisePython;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisJobDAO analysisJobDAO;
    private final ClientDAO clientDAO;
    private final MeetingDAO meetingDAO;
    private final FindingDAO findingDAO;
    private final ServicoAnalisePython servicoAnalisePython;
    private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisController(AnalysisJobDAO analysisJobDAO, ClientDAO clientDAO, MeetingDAO meetingDAO,
                               FindingDAO findingDAO, ServicoAnalisePython servicoAnalisePython) {
        this.analysisJobDAO = analysisJobDAO;
        this.clientDAO = clientDAO;
        this.meetingDAO = meetingDAO;
        this.findingDAO = findingDAO;
        this.servicoAnalisePython = servicoAnalisePython;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public AnalysisJobResponse upload(Authentication authentication,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam("client_id") Long clientId) {
        Long userId = ((User) authentication.getPrincipal()).getId();

        Client client;
        try {
            client = clientDAO.buscarPorId(clientId);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao validar cliente", e);
        }
        if (client == null || !client.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado");
        }

        AnalysisJob job = new AnalysisJob(userId, clientId, file.getOriginalFilename(), "");
        try {
            analysisJobDAO.inserir(job);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao registrar job de análise", e);
        }

        processar(job, file);
        return paraResposta(job, client);
    }

    private AnalysisJobResponse paraResposta(AnalysisJob job, Client client) {
        MeetingDetailResponse meetingResponse = null;
        if (job.getMeetingId() != null) {
            try {
                Meeting meeting = meetingDAO.buscarPorId(job.getMeetingId());
                if (meeting != null) {
                    meetingResponse = new MeetingDetailResponse(meeting, client.getName());
                }
            } catch (SQLException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao carregar reunião do job", e);
            }
        }
        return new AnalysisJobResponse(job, meetingResponse);
    }

    private void processar(AnalysisJob job, MultipartFile file) {
        try {
            job.iniciarProcessamento();
            analysisJobDAO.atualizar(job);

            byte[] bytes = file.getBytes();
            String respostaJson = servicoAnalisePython.enviarParaProcessamento(bytes, file.getOriginalFilename());
            JsonNode resposta = objectMapper.readTree(respostaJson);

            Meeting meeting = new Meeting(job.getClientId(), job.getSourceFilename());
            meeting.setTriage(textoOuVazio(resposta, "triage"));
            meeting.setSelectedAgentsJson(arrayOuPadrao(resposta, "selected_agents", "[]"));
            meeting.setFinalReportJson(objetoOuPadrao(resposta, "final_report", "{}"));
            meeting.setReportsJson(arrayOuPadrao(resposta, "reports", "[]"));
            meetingDAO.inserir(meeting);
            registrarFindings(job, meeting, resposta.path("final_report"));

            job.concluirComSucesso(meeting.getId());
            analysisJobDAO.atualizar(job);
        } catch (AnalisePythonException | IOException | SQLException e) {
            job.falhar(e.getMessage());
            try {
                analysisJobDAO.atualizar(job);
            } catch (SQLException ignored) {
                // job já registra a falha em memória; resposta ao cliente é o que importa aqui
            }
        }
    }

    /** Falha ao gravar achados não derruba a análise: a reunião já foi salva. */
    private void registrarFindings(AnalysisJob job, Meeting meeting, JsonNode finalReport) {
        try {
            Set<String> abertos = findingDAO.listarPorCliente(job.getClientId()).stream()
                    .filter(f -> !Finding.STATUS_RESOLVED.equals(f.getStatus())
                            && !Finding.STATUS_DISMISSED.equals(f.getStatus()))
                    .map(Finding::getFindingType)
                    .collect(Collectors.toSet());
            List<Finding> novos = FindingsDaAnalise.extrair(finalReport, job.getClientId(), meeting.getId(), abertos);
            for (Finding finding : novos) {
                findingDAO.inserir(finding);
            }
        } catch (SQLException e) {
            logger.warn("Reunião {} salva, mas os achados não puderam ser gravados", meeting.getId(), e);
        }
    }

    private String textoOuVazio(JsonNode node, String campo) {
        return node.hasNonNull(campo) ? node.get(campo).asText() : "";
    }

    private String arrayOuPadrao(JsonNode node, String campo, String padrao) {
        return node.hasNonNull(campo) ? node.get(campo).toString() : padrao;
    }

    private String objetoOuPadrao(JsonNode node, String campo, String padrao) {
        return node.hasNonNull(campo) ? node.get(campo).toString() : padrao;
    }

    @GetMapping("/jobs/{id}")
    public AnalysisJobResponse consultarJob(Authentication authentication, @PathVariable Long id) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        try {
            AnalysisJob job = analysisJobDAO.buscarPorId(id);
            if (job == null || !job.getUserId().equals(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Job de análise não encontrado");
            }
            Client client = clientDAO.buscarPorId(job.getClientId());
            return paraResposta(job, client);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao consultar job de análise", e);
        }
    }
}
