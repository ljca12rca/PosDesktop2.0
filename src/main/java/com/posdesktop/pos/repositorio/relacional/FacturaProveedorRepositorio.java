package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.FacturaProveedor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaProveedorRepositorio extends JpaRepository<FacturaProveedor, UUID> {

    List<FacturaProveedor> findByProveedorId(UUID proveedorId);
}
