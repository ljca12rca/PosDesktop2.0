package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Separado;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeparadoRepositorio extends JpaRepository<Separado, UUID> {

    Optional<Separado> findByNumeroSeparado(String numeroSeparado);
}
