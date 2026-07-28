package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.UsuarioSistema;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioSistemaRepositorio extends JpaRepository<UsuarioSistema, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permisos"})
    Optional<UsuarioSistema> findByUsernameIgnoreCase(String username);
}
