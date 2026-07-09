package com.posdesktop.pos.separados.service;

import com.posdesktop.pos.repositorio.relacional.SeparadoRepositorio;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class SeparadoNumeroService {

    private static final DateTimeFormatter NUMERO_DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SeparadoRepositorio separadoRepositorio;

    public SeparadoNumeroService(SeparadoRepositorio separadoRepositorio) {
        this.separadoRepositorio = separadoRepositorio;
    }

    public String generarNumero(LocalDate fechaSeparacion) {
        LocalDate fecha = fechaSeparacion == null ? LocalDate.now() : fechaSeparacion;
        long consecutivo = separadoRepositorio.countByFechaSeparacion(fecha) + 1;
        return "SP-" + fecha.format(NUMERO_DIA) + "-" + String.format("%04d", consecutivo);
    }
}
