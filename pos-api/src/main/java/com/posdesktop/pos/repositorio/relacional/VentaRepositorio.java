package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Venta;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaRepositorio extends JpaRepository<Venta, UUID> {
    java.util.Optional<Venta> findByNumeroVenta(String numeroVenta);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Venta v where v.id = :id")
    java.util.Optional<Venta> findForFiscalUpdate(UUID id);

    List<Venta> findByFechaVentaBetween(LocalDateTime inicio, LocalDateTime fin);

    List<Venta> findByFechaVentaBetweenOrderByFechaVentaDesc(LocalDateTime inicio, LocalDateTime fin);

    long countByFechaVentaBetween(LocalDateTime inicio, LocalDateTime fin);
}
