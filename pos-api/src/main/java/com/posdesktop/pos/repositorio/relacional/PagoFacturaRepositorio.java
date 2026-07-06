package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.PagoFactura;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoFacturaRepositorio extends JpaRepository<PagoFactura, UUID> {

    List<PagoFactura> findByFacturaProveedorId(UUID facturaProveedorId);
}
