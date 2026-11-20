package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalDeliverySettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface FiscalDeliverySettingsRepository extends JpaRepository<FiscalDeliverySettings,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from FiscalDeliverySettings c where c.id=1") Optional<FiscalDeliverySettings> lock();
}
