package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalDocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FiscalDocumentRepository extends JpaRepository<FiscalDocumentRecord,UUID> {
    Optional<FiscalDocumentRecord> findByHuella(String hash);
    List<FiscalDocumentRecord> findTop20ByFormatoAndNumeroContainingIgnoreCaseOrderByCreadoEnDesc(String format,String number);
    boolean existsByPropositoAndProveedorAndNumero(String purpose,String provider,String number);
    List<FiscalDocumentRecord> findTop200ByOrderByCreadoEnDesc();
}
