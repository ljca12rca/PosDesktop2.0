package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.enumeraciones.EstadoFacturaProveedor;
import com.posdesktop.pos.modelo.relacional.FacturaProveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaProveedorRepositorio extends JpaRepository<FacturaProveedor, UUID> {

    List<FacturaProveedor> findByProveedorId(UUID proveedorId);

    List<FacturaProveedor> findAllByOrderByFechaEmisionDescNumeroFacturaDesc();

    List<FacturaProveedor> findByEstadoOrderByFechaEmisionDescNumeroFacturaDesc(EstadoFacturaProveedor estado);

    List<FacturaProveedor> findByProveedorIdOrderByFechaEmisionDescNumeroFacturaDesc(UUID proveedorId);

    List<FacturaProveedor> findByProveedorIdAndEstadoOrderByFechaEmisionDescNumeroFacturaDesc(
            UUID proveedorId,
            EstadoFacturaProveedor estado
    );

    Optional<FacturaProveedor> findByProveedorIdAndNumeroFacturaIgnoreCase(UUID proveedorId, String numeroFactura);
}
