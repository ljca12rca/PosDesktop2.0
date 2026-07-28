package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.SesionUsuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionUsuarioRepositorio extends JpaRepository<SesionUsuario, UUID> {

    @EntityGraph(attributePaths = {"usuario", "usuario.roles", "usuario.roles.permisos"})
    Optional<SesionUsuario> findByTokenAndActivaTrue(String token);
}
