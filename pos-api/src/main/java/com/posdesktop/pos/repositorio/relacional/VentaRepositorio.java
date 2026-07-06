package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Venta;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaRepositorio extends JpaRepository<Venta, UUID> {

    List<Venta> findByFechaVentaBetween(LocalDateTime inicio, LocalDateTime fin);

    List<Venta> findByFechaVentaBetweenOrderByFechaVentaDesc(LocalDateTime inicio, LocalDateTime fin);

    long countByFechaVentaBetween(LocalDateTime inicio, LocalDateTime fin);
}
