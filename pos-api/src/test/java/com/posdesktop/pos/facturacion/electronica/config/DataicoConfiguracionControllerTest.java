package com.posdesktop.pos.facturacion.electronica.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.AuthService;
import com.posdesktop.pos.auth.service.AuthSessionData;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DataicoConfiguracionControllerTest {
    private final DataicoConfiguracionService service = mock(DataicoConfiguracionService.class);
    private final DataicoConfiguracionController controller = new DataicoConfiguracionController(service);

    @Test
    void unVendedorNoPuedeConsultarNiModificarLaConfiguracion() {
        var request = request("VENDEDOR");
        assertThatThrownBy(() -> controller.consultar(request)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.guardar(null, request)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service);
    }

    @Test
    void permiteAdministrarLaConfiguracionAlRolAdmin() {
        var request = request("ADMIN");
        var body = new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L, "PRODUCCION", true, 0L, 1789500100L);
        controller.consultar(request);
        controller.guardar(body, request);
        verify(service).consultar();
        verify(service).guardar(body);
    }

    private MockHttpServletRequest request(String rol) {
        var request = new MockHttpServletRequest();
        request.setAttribute(AuthService.REQUEST_CONTEXT,
                new AuthSessionData(null, null, null, "usuario-prueba", "Prueba", Set.of(rol), Set.of("VENTAS_EDIT")));
        return request;
    }
}
