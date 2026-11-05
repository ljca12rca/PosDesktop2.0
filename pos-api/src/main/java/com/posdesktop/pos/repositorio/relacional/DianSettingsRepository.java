package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.DianSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface DianSettingsRepository extends JpaRepository<DianSettings,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from DianSettings s where s.ambiente = :environment")
    Optional<DianSettings> lockSettings(String environment);
}
