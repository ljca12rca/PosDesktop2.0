package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Proveedor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepositorio extends JpaRepository<Proveedor, UUID> {

    List<Proveedor> findAllByOrderByNombreAsc();

    Optional<Proveedor> findByNit(String nit);

    Optional<Proveedor> findByNitIgnoreCase(String nit);
}
