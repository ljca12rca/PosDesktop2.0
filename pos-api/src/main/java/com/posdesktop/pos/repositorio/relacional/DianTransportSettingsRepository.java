package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.DianTransportSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface DianTransportSettingsRepository extends JpaRepository<DianTransportSettings,Long>{
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from DianTransportSettings c where c.id=1") Optional<DianTransportSettings> lock();
}
