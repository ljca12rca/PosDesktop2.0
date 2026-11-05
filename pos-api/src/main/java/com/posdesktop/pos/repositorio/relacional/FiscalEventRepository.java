package com.posdesktop.pos.repositorio.relacional;
import com.posdesktop.pos.modelo.relacional.FiscalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FiscalEventRepository extends JpaRepository<FiscalEvent,UUID> {
    List<FiscalEvent> findByDocumentoIdOrderByCreadoEnAsc(UUID documentId);
}
