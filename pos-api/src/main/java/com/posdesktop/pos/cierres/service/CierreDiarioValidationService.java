package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.cierres.api.dto.RegistrarCierreRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class CierreDiarioValidationService {

    public void validarConsulta(LocalDate fechaOperacion) {
        if (fechaOperacion != null && fechaOperacion.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("No es posible consultar cierres para fechas futuras.");
        }
    }

    public void validarRegistro(RegistrarCierreRequest request) {
        if (request.fechaOperacion().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("No es posible guardar cierres para fechas futuras.");
        }
    }

    public void validarRango(LocalDate fechaInicial, LocalDate fechaFinal) {
        if (fechaFinal.isBefore(fechaInicial)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (fechaInicial.isAfter(LocalDate.now()) || fechaFinal.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("No es posible consultar cierres con fechas futuras.");
        }
    }
}
