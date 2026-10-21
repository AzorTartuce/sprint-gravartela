package com.sprintjava.controller;

import com.sprintjava.controller.dto.CreateAttemptRequest;
import com.sprintjava.controller.dto.FindingRequest;
import com.sprintjava.controller.dto.UpdateAttemptRequest;
import com.sprintjava.dao.ClientDAO;
import com.sprintjava.dao.FindingAttemptDAO;
import com.sprintjava.dao.FindingDAO;
import com.sprintjava.dao.MeetingDAO;
import com.sprintjava.dao.StrategyDAO;
import com.sprintjava.exception.ApiException;
import com.sprintjava.model.Client;
import com.sprintjava.model.Finding;
import com.sprintjava.model.FindingAttempt;
import com.sprintjava.model.Meeting;
import com.sprintjava.model.Strategy;
import com.sprintjava.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/findings")
public class FindingController {

    private static final Set<String> SEVERIDADES = Set.of("low", "medium", "high", "critical");

    private final FindingDAO findingDAO;
    private final FindingAttemptDAO findingAttemptDAO;
    private final ClientDAO clientDAO;
    private final StrategyDAO strategyDAO;
    private final MeetingDAO meetingDAO;

    public FindingController(FindingDAO findingDAO, FindingAttemptDAO findingAttemptDAO, ClientDAO clientDAO,
                              StrategyDAO strategyDAO, MeetingDAO meetingDAO) {
        this.findingDAO = findingDAO;
        this.findingAttemptDAO = findingAttemptDAO;
        this.clientDAO = clientDAO;
        this.strategyDAO = strategyDAO;
        this.meetingDAO = meetingDAO;
    }

    @GetMapping
    public Map<String, List<Finding>> listarPorCliente(Authentication authentication,
                                                         @RequestParam("client_id") Long clientId) {
        garantirClienteDoUsuario(authentication, clientId);
        try {
            List<Finding> findings = findingDAO.listarPorCliente(clientId);
            for (Finding finding : findings) {
                finding.setAttempts(findingAttemptDAO.listarPorFinding(finding.getId()));
            }
            return Map.of("items", findings);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao listar findings", e);
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Finding criar(Authentication authentication, @RequestBody FindingRequest request) {
        if (request.getClientId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "client_id é obrigatório");
        }
        if (isBlank(request.getFindingType()) || isBlank(request.getTitle())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "finding_type e title são obrigatórios");
        }
        if (request.getSeverityHint() != null && !SEVERIDADES.contains(request.getSeverityHint())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "severity_hint deve ser low, medium, high ou critical");
        }
        garantirClienteDoUsuario(authentication, request.getClientId());
        try {
            if (request.getOriginMeetingId() != null) {
                Meeting meeting = meetingDAO.buscarPorId(request.getOriginMeetingId());
                if (meeting == null || !request.getClientId().equals(meeting.getClientId())) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "Reunião não encontrada para este cliente");
                }
            }
            Finding finding = new Finding(request.getClientId(), request.getOriginMeetingId(),
                    request.getFindingType().trim(), request.getTitle().trim(), request.getSeverityHint());
            if (request.getStatus() != null) finding.atualizarStatus(request.getStatus());
            findingDAO.inserir(finding);
            return finding;
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao criar finding", e);
        }
    }

    @PatchMapping("/{id}")
    public Finding atualizar(Authentication authentication, @PathVariable Long id, @RequestBody FindingRequest request) {
        Finding finding = buscarFindingDoUsuario(authentication, id);
        if (request.getTitle() != null) finding.setTitle(request.getTitle());
        if (request.getSeverityHint() != null) finding.setSeverityHint(request.getSeverityHint());
        if (request.getStatus() != null) finding.atualizarStatus(request.getStatus());
        try {
            findingDAO.atualizar(finding);
            finding.setAttempts(findingAttemptDAO.listarPorFinding(finding.getId()));
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao atualizar finding", e);
        }
        return finding;
    }

    @PostMapping("/{id}/attempts")
    public Finding registrarTentativa(Authentication authentication, @PathVariable Long id,
                                       @RequestBody CreateAttemptRequest request) {
        Finding finding = buscarFindingDoUsuario(authentication, id);
        try {
            Strategy strategy = strategyDAO.buscarPorKey(request.getStrategyKey());
            String label = strategy != null ? strategy.getLabel() : request.getStrategyKey();
            FindingAttempt attempt = new FindingAttempt(finding.getId(), request.getStrategyKey(), label,
                    request.getNotes());
            findingAttemptDAO.inserir(attempt);

            finding.atualizarStatus(Finding.STATUS_IN_PROGRESS);
            findingDAO.atualizar(finding);
            finding.setAttempts(findingAttemptDAO.listarPorFinding(finding.getId()));
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao registrar tentativa", e);
        }
        return finding;
    }

    @PatchMapping("/{id}/attempts/{attemptId}")
    public Finding atualizarTentativa(Authentication authentication, @PathVariable Long id,
                                       @PathVariable Long attemptId, @RequestBody UpdateAttemptRequest request) {
        Finding finding = buscarFindingDoUsuario(authentication, id);
        try {
            FindingAttempt attempt = findingAttemptDAO.buscarPorId(attemptId);
            if (attempt == null || !attempt.getFindingId().equals(finding.getId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Tentativa não encontrada");
            }
            attempt.registrarResultado(request.getOutcome(), request.getNotes());
            findingAttemptDAO.atualizar(attempt);
            finding.setAttempts(findingAttemptDAO.listarPorFinding(finding.getId()));
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao atualizar tentativa", e);
        }
        return finding;
    }

    /** Remove o finding; tentativas e sugestões são apagadas em cascata pelo banco. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(Authentication authentication, @PathVariable Long id) {
        Finding finding = buscarFindingDoUsuario(authentication, id);
        try {
            findingDAO.deletar(finding.getId());
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao excluir finding", e);
        }
        return ResponseEntity.noContent().build();
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }

    private Finding buscarFindingDoUsuario(Authentication authentication, Long id) {
        try {
            Finding finding = findingDAO.buscarPorId(id);
            if (finding == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Finding não encontrado");
            }
            garantirClienteDoUsuario(authentication, finding.getClientId());
            return finding;
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao buscar finding", e);
        }
    }

    private void garantirClienteDoUsuario(Authentication authentication, Long clientId) {
        try {
            Client client = clientDAO.buscarPorId(clientId);
            Long userId = ((User) authentication.getPrincipal()).getId();
            if (client == null || !client.getUserId().equals(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado");
            }
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao validar cliente", e);
        }
    }
}
