package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.DataicoConsecutivo;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataicoConsecutivoRepositorio extends JpaRepository<DataicoConsecutivo, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select consecutivo from DataicoConsecutivo consecutivo where consecutivo.prefijo = :prefijo")
    Optional<DataicoConsecutivo> findByPrefijoForUpdate(@Param("prefijo") String prefijo);
}
