package com.posdesktop.pos.repositorio.documental;

import com.posdesktop.pos.modelo.documental.DocumentoSoporte;
import com.posdesktop.pos.modelo.enumeraciones.EntidadOrigenDocumento;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentoSoporteRepositorio extends MongoRepository<DocumentoSoporte, String> {

    List<DocumentoSoporte> findByEntidadOrigenAndEntidadOrigenId(
            EntidadOrigenDocumento entidadOrigen,
            String entidadOrigenId
    );

    List<DocumentoSoporte> findByEntidadOrigenAndEntidadOrigenIdIn(
            EntidadOrigenDocumento entidadOrigen,
            List<String> entidadOrigenIds
    );
}
