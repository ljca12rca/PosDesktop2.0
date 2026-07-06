package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleVentaRepositorio extends JpaRepository<DetalleVenta, UUID> {

    List<DetalleVenta> findByVentaId(UUID ventaId);
}
