package com.sprintjava.security;

import com.sprintjava.dao.UserDAO;
import com.sprintjava.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Não há login: toda requisição é atendida como o usuário padrão
 * (app.usuario-padrao-id), dono dos clientes e achados.
 */
@Component
public class UsuarioPadraoFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioPadraoFilter.class);

    private final UserDAO userDAO;
    private final Long usuarioPadraoId;
    private volatile User usuarioPadrao;

    public UsuarioPadraoFilter(UserDAO userDAO, @Value("${app.usuario-padrao-id:1}") Long usuarioPadraoId) {
        this.userDAO = userDAO;
        this.usuarioPadraoId = usuarioPadraoId;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        User user = carregarUsuarioPadrao();
        if (user != null) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        }
        filterChain.doFilter(request, response);
    }

    private User carregarUsuarioPadrao() {
        if (usuarioPadrao == null) {
            try {
                usuarioPadrao = userDAO.buscarPorId(usuarioPadraoId);
            } catch (SQLException e) {
                logger.error("Não foi possível carregar o usuário padrão {}", usuarioPadraoId, e);
            }
            if (usuarioPadrao == null) {
                logger.warn("Usuário padrão {} não encontrado", usuarioPadraoId);
            }
        }
        return usuarioPadrao;
    }
}
