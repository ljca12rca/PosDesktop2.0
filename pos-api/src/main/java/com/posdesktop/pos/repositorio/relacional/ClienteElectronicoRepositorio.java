package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.ClienteElectronico;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteElectronicoRepositorio extends JpaRepository<ClienteElectronico, UUID> {
    Optional<ClienteElectronico> findByTipoIdentificacionAndIdentificacion(String tipoIdentificacion, String identificacion);

    @Query("""
            select cliente from ClienteElectronico cliente
            where lower(cliente.primerNombre) like lower(concat(concat('%', :termino), '%'))
               or lower(cliente.segundoNombre) like lower(concat(concat('%', :termino), '%'))
               or lower(cliente.primerApellido) like lower(concat(concat('%', :termino), '%'))
               or lower(cliente.segundoApellido) like lower(concat(concat('%', :termino), '%'))
               or cliente.identificacion like concat(concat('%', :termino), '%')
            order by cliente.primerApellido, cliente.primerNombre, cliente.razonSocial
            """)
    List<ClienteElectronico> buscarParaVenta(@Param("termino") String termino);
}
