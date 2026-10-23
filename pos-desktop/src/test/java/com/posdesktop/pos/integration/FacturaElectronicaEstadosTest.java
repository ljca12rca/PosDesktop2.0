package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FacturaElectronicaEstadosTest {
    @Test
    void dianSoloEsVerdeCuandoLaAceptacionEstaConfirmada() {
        assertTrue(factura("DIAN_ACEPTADO", null).dianAceptada());
        assertFalse(factura("DIAN_RECHAZADO", null).dianAceptada());
        assertFalse(factura("DIAN_NO_ENVIADO", null).dianAceptada());
        assertFalse(factura("DIAN_PENDIENTE", null).dianAceptada());
        assertFalse(factura(null, null).dianAceptada());
    }

    @Test
    void correoNoConfundeNoEnviadoConEnviado() {
        assertTrue(factura(null, "ENVIADO").correoEnviado());
        assertTrue(factura(null, "CORREO_ENVIADO").correoEnviado());
        for (String estado : new String[]{"NO_ENVIADO", "CORREO_NO_ENVIADO", "NO_SOLICITADO", "PENDIENTE", "ERROR"}) {
            assertFalse(factura(null, estado).correoEnviado(), estado);
        }
        assertFalse(factura(null, null).correoEnviado());
    }

    private PosApiClient.FacturaElectronicaListadoResponse factura(String dian, String correo) {
        return new PosApiClient.FacturaElectronicaListadoResponse("id", null, "FEE", "FEE1", "Cliente", "123",
                null, null, null, dian, null, correo, null, null, null, null);
    }
}
