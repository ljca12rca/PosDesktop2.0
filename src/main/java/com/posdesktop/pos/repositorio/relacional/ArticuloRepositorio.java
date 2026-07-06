package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Articulo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticuloRepositorio extends JpaRepository<Articulo, UUID> {

    Optional<Articulo> findBySku(String sku);
}
