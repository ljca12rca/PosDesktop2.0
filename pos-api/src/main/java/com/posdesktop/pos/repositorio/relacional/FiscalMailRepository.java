package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalMailAttempt;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface FiscalMailRepository extends JpaRepository<FiscalMailAttempt,UUID>{
 List<FiscalMailAttempt> findByDocumentIdOrderByCreatedAtDesc(UUID id);
 boolean existsByDocumentIdAndRecipientAndStateIn(UUID id,String recipient,Collection<String> states);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from FiscalMailAttempt c where c.id=:id") Optional<FiscalMailAttempt> lock(UUID id);
}
