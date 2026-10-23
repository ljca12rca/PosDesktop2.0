package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.DataicoConfiguracion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DataicoConfiguracionRepositorio extends JpaRepository<DataicoConfiguracion, Integer> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from DataicoConfiguracion c where c.id = 1")
    Optional<DataicoConfiguracion> findForEmission();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DataicoConfiguracion c where c.id = 1")
    Optional<DataicoConfiguracion> findForUpdate();

    @Modifying
    @Query(value = "INSERT INTO dataico_configuracion (id, version) VALUES (1, 0) ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void asegurarRegistro();
}
