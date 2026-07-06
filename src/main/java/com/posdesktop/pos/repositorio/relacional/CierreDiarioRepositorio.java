package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.CierreDiario;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CierreDiarioRepositorio extends JpaRepository<CierreDiario, UUID> {

    Optional<CierreDiario> findByFechaOperacion(LocalDate fechaOperacion);
}
