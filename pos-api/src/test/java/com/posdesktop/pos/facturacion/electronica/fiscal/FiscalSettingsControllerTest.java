package com.posdesktop.pos.facturacion.electronica.fiscal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Set;

class FiscalSettingsControllerTest {
    private final FiscalSettingsService service=mock(FiscalSettingsService.class);
    private final FiscalSettingsController controller=new FiscalSettingsController(service);
    @Test void configurarProveedorYCredencialesRequiereAdministrador(){
        var request=request("CAJERO");
        assertThrows(ForbiddenException.class,()->controller.saveProvider(new FiscalSettingsService.SaveProvider("DIAN_DIRECT",0),request));
        assertThrows(ForbiddenException.class,()->controller.dian("PRUEBAS",request));
        assertThrows(ForbiddenException.class,()->controller.saveDian("PRODUCCION",null,request));
        verifyNoInteractions(service);
    }
    @Test void administradorPuedeLeerAmbienteYModificarSeleccion(){
        var request=request("ADMIN");
        var body=new FiscalSettingsService.SaveProvider("DIAN_DIRECT",0);
        controller.saveProvider(body,request);controller.dian("PRUEBAS",request);
        verify(service).saveProvider(body);verify(service).dian("PRUEBAS");
    }
    @Test void registrosDeSolicitudYSecretosNuncaExponenLosValores(){
        var request=new FiscalSettingsService.SaveDian(0,null,"CLAVE-PRUEBA","PIN-PRUEBA","CERTIFICADO-PRUEBA","PASSWORD-PRUEBA");
        var secrets=new FiscalSecretVault.Secrets("PRUEBAS","CLAVE-PRUEBA","PIN-PRUEBA","CERTIFICADO-PRUEBA","PASSWORD-PRUEBA");
        for(String secret: new String[]{"CLAVE-PRUEBA","PIN-PRUEBA","CERTIFICADO-PRUEBA","PASSWORD-PRUEBA"}){
            assertFalse(request.toString().contains(secret));assertFalse(secrets.toString().contains(secret));
        }
    }
    private MockHttpServletRequest request(String role){var request=new MockHttpServletRequest();request.setAttribute(AuthService.REQUEST_CONTEXT,
            new AuthSessionData(null,null,null,"test","Prueba",Set.of(role),Set.of("VENTAS_VIEW")));return request;}
}
