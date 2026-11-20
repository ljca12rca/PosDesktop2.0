package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.DianSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface DianSubmissionRepository extends JpaRepository<DianSubmission,UUID>{
    Optional<DianSubmission> findByDocumentIdAndMode(UUID id,String mode);
    List<DianSubmission> findByLogicalKeyOrderByCreatedAtDesc(String key);
    List<DianSubmission> findByDocumentIdOrderByCreatedAtDesc(UUID id);
    List<DianSubmission> findByDocumentIdIn(Collection<UUID> ids);
    boolean existsBySourceDocumentIdAndStateIn(UUID id,Collection<String> states);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from DianSubmission e where e.id=:id") Optional<DianSubmission> lock(UUID id);
}
