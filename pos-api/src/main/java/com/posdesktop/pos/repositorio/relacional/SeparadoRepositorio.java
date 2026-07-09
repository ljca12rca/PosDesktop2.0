package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Separado;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeparadoRepositorio extends JpaRepository<Separado, UUID> {

    Optional<Separado> findByNumeroSeparado(String numeroSeparado);

    long countByFechaSeparacion(LocalDate fechaSeparacion);

    List<Separado> findAllByOrderByFechaSeparacionDescNumeroSeparadoDesc();

    List<Separado> findByEstadoOrderByFechaSeparacionDescNumeroSeparadoDesc(
            com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado estado
    );

    List<Separado> findByDescripcionArticuloContainingIgnoreCaseOrderByFechaSeparacionDescNumeroSeparadoDesc(
            String descripcionArticulo
    );

    List<Separado> findByEstadoAndDescripcionArticuloContainingIgnoreCaseOrderByFechaSeparacionDescNumeroSeparadoDesc(
            com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado estado,
            String descripcionArticulo
    );
}
