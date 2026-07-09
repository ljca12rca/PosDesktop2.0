package com.posdesktop.pos.separados.service;

import com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado;
import com.posdesktop.pos.modelo.relacional.Separado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class SeparadoValidationService {

    private static final BigDecimal MONTO_MINIMO_INICIAL = new BigDecimal("20000.00");

    public void validarRegistro(BigDecimal valorTotal, BigDecimal abonoInicial) {
        BigDecimal totalNormalizado = normalizarDinero(valorTotal);
        BigDecimal abonoNormalizado = normalizarDinero(abonoInicial);

        if (totalNormalizado.signum() <= 0) {
            throw new IllegalArgumentException("El valor total del separado debe ser mayor a cero.");
        }
        if (abonoNormalizado.compareTo(MONTO_MINIMO_INICIAL) < 0) {
            throw new IllegalArgumentException("El abono inicial minimo es de 20.000 COP.");
        }
        if (abonoNormalizado.compareTo(totalNormalizado) > 0) {
            throw new IllegalArgumentException("El abono inicial no puede ser mayor al valor total del separado.");
        }
    }

    public void validarNuevoAbono(Separado separado, BigDecimal valorAbono) {
        if (separado == null) {
            throw new IllegalArgumentException("El separado es obligatorio para registrar un abono.");
        }
        if (separado.getEstado() == EstadoSeparado.CANCELADO || separado.getEstado() == EstadoSeparado.ENTREGADO) {
            throw new IllegalArgumentException("No es posible registrar abonos para separados cerrados.");
        }
        if (separado.getSaldoPendiente() == null || separado.getSaldoPendiente().signum() <= 0) {
            throw new IllegalArgumentException("El separado ya no tiene saldo pendiente.");
        }

        BigDecimal abonoNormalizado = normalizarDinero(valorAbono);
        if (abonoNormalizado.signum() <= 0) {
            throw new IllegalArgumentException("El valor del abono debe ser mayor a cero.");
        }
        if (abonoNormalizado.compareTo(normalizarDinero(separado.getSaldoPendiente())) > 0) {
            throw new IllegalArgumentException("El abono no puede superar el saldo pendiente del separado.");
        }
    }

    private BigDecimal normalizarDinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
