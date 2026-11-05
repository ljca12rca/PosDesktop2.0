package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalProviderSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface FiscalProviderSettingsRepository extends JpaRepository<FiscalProviderSettings,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from FiscalProviderSettings s where s.id = 1")
    Optional<FiscalProviderSettings> lockSettings();
}
