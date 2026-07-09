package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.AbonoSeparado;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbonoSeparadoRepositorio extends JpaRepository<AbonoSeparado, UUID> {

    List<AbonoSeparado> findBySeparadoIdOrderByNumeroAbonoAsc(UUID separadoId);
}
