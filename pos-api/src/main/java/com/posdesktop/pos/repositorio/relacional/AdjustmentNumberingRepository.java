package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.AdjustmentNumbering;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AdjustmentNumberingRepository extends JpaRepository<AdjustmentNumbering,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from AdjustmentNumbering n where n.id=:id") Optional<AdjustmentNumbering> lock(String id);
}
