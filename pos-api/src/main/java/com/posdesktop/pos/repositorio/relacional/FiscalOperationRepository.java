package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalOperation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface FiscalOperationRepository extends JpaRepository<FiscalOperation,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from FiscalOperation o where o.ventaId = :saleId")
    Optional<FiscalOperation> lockOperation(UUID saleId);
}
