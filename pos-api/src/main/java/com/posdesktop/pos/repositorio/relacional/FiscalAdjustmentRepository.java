package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalAdjustmentRecord;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface FiscalAdjustmentRepository extends JpaRepository<FiscalAdjustmentRecord,UUID> {
    Optional<FiscalAdjustmentRecord> findByRequestId(UUID id);
    Optional<FiscalAdjustmentRecord> findByDocumentId(UUID id);
    List<FiscalAdjustmentRecord> findByDocumentIdIn(Collection<UUID> ids);
    List<FiscalAdjustmentRecord> findBySourceKeyOrderByCreatedAtAsc(String key);
    List<FiscalAdjustmentRecord> findTop100ByOrderByCreatedAtDesc();
    boolean existsBySourceKeyStartingWithAndNumber(String context,String number);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from FiscalAdjustmentRecord a where a.id=:id") Optional<FiscalAdjustmentRecord> lock(UUID id);
}
