package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaElectronicaRepositorio extends JpaRepository<FacturaElectronica, UUID> {
    Optional<FacturaElectronica> findByVentaId(UUID ventaId);
    boolean existsByNumeroFactura(String numeroFactura);
    List<FacturaElectronica> findTop20ByNumeroFacturaContainingIgnoreCaseOrClienteNombreContainingIgnoreCaseOrderByFechaEmisionDesc(String number,String customer);
    List<FacturaElectronica> findAllByOrderByFechaEmisionDescNumeroFacturaDesc();
    Optional<FacturaElectronica> findFirstByNumeracionOrderByFechaEmisionDesc(String numeracion);
}
