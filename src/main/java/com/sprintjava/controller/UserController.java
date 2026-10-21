package com.sprintjava.controller;

import com.sprintjava.controller.dto.UpdateProfileRequest;
import com.sprintjava.controller.dto.UserResponse;
import com.sprintjava.dao.UserDAO;
import com.sprintjava.exception.ApiException;
import com.sprintjava.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDAO userDAO;

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @GetMapping
    public List<UserResponse> listarTodos() {
        try {
            return userDAO.listarTodos().stream().map(UserResponse::new).toList();
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao listar usuários", e);
        }
    }

    @GetMapping("/{id}")
    public UserResponse buscarPorId(@PathVariable Long id) {
        return new UserResponse(buscar(id));
    }

    /** Substitui os dados de perfil; só o próprio usuário pode alterar o seu cadastro. */
    @PutMapping("/{id}")
    public UserResponse atualizar(Authentication authentication, @PathVariable Long id,
                                  @RequestBody UpdateProfileRequest request) {
        garantirProprioUsuario(authentication, id);
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "full_name é obrigatório");
        }
        User user = buscar(id);
        user.setFullName(request.getFullName());
        user.setCompany(request.getCompany());
        user.setJobTitle(request.getJobTitle());
        user.setDepartment(request.getDepartment());
        user.setPhone(request.getPhone());
        user.setCity(request.getCity());
        user.setState(request.getState());
        user.setLinkedinUrl(request.getLinkedinUrl());
        user.setBio(request.getBio());
        try {
            userDAO.atualizar(user);
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao atualizar usuário", e);
        }
        return new UserResponse(user);
    }

    /** Exclui a própria conta; clientes e demais dados são apagados em cascata pelo banco. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(Authentication authentication, @PathVariable Long id) {
        garantirProprioUsuario(authentication, id);
        try {
            if (!userDAO.deletar(id)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
            }
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao excluir usuário", e);
        }
        return ResponseEntity.noContent().build();
    }

    private User buscar(Long id) {
        try {
            User user = userDAO.buscarPorId(id);
            if (user == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
            }
            return user;
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao buscar usuário", e);
        }
    }

    private void garantirProprioUsuario(Authentication authentication, Long id) {
        Long logado = ((User) authentication.getPrincipal()).getId();
        if (!logado.equals(id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Só é possível alterar o próprio usuário");
        }
    }
}
