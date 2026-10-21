package com.sprintjava.controller;

import com.sprintjava.controller.dto.StrategyRequest;
import com.sprintjava.dao.StrategyDAO;
import com.sprintjava.exception.ApiException;
import com.sprintjava.model.Strategy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/strategies")
public class StrategyController {

    private final StrategyDAO strategyDAO;

    public StrategyController(StrategyDAO strategyDAO) {
        this.strategyDAO = strategyDAO;
    }

    @GetMapping
    public Map<String, List<Strategy>> listar(@RequestParam(value = "finding_type", required = false) String findingType) {
        try {
            List<Strategy> strategies = strategyDAO.listarTodas();
            if (findingType != null && !findingType.isBlank()) {
                strategies = strategies.stream().filter(s -> s.aplicaA(findingType)).collect(Collectors.toList());
            }
            return Map.of("items", strategies);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao listar estratégias", e);
        }
    }

    @GetMapping("/{id}")
    public Strategy buscarPorId(@PathVariable Long id) {
        return buscarOuFalhar(id);
    }

    @PostMapping
    public ResponseEntity<Strategy> criar(@RequestBody StrategyRequest request) {
        if (request.getKey() == null || request.getKey().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Chave da estratégia é obrigatória");
        }
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Rótulo da estratégia é obrigatório");
        }
        String key = request.getKey().trim();
        try {
            if (strategyDAO.buscarPorKey(key) != null) {
                throw new ApiException(HttpStatus.CONFLICT, "Já existe uma estratégia com essa chave");
            }
            Strategy strategy = new Strategy(null, key, request.getLabel().trim(),
                    request.getAppliesTo() != null ? request.getAppliesTo() : List.of("*"), false, true);
            strategyDAO.inserir(strategy);
            return ResponseEntity.status(HttpStatus.CREATED).body(strategy);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao criar estratégia", e);
        }
    }

    @PutMapping("/{id}")
    public Strategy atualizar(@PathVariable Long id, @RequestBody StrategyRequest request) {
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Rótulo da estratégia é obrigatório");
        }
        Strategy strategy = buscarOuFalhar(id);
        exigirCustomizada(strategy);
        strategy.setLabel(request.getLabel().trim());
        if (request.getAppliesTo() != null) {
            strategy.setAppliesTo(request.getAppliesTo());
        }
        try {
            strategyDAO.atualizar(strategy);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao atualizar estratégia", e);
        }
        return strategy;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        exigirCustomizada(buscarOuFalhar(id));
        try {
            strategyDAO.deletar(id);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao excluir estratégia", e);
        }
        return ResponseEntity.noContent().build();
    }

    private Strategy buscarOuFalhar(Long id) {
        try {
            return strategyDAO.listarTodas().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Estratégia não encontrada"));
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao buscar estratégia", e);
        }
    }

    /** Estratégias nativas fazem parte do catálogo do sistema e não podem ser alteradas. */
    private void exigirCustomizada(Strategy strategy) {
        if (strategy.isBuiltin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Estratégias nativas não podem ser alteradas ou excluídas");
        }
    }
}
