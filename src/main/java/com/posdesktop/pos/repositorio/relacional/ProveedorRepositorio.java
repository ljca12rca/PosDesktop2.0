package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Proveedor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepositorio extends JpaRepository<Proveedor, UUID> {

    Optional<Proveedor> findByNit(String nit);
}
