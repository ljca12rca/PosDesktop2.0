package com.posdesktop.pos.facturacion.electronica.adjustments;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdjustmentControllerTest {
    @Test void numberingConfigurationRequiresAdminBeforeAnyServiceCall(){var service=mock(AdjustmentService.class);var resolver=mock(AdjustmentSourceResolver.class);var controller=new AdjustmentController(service,resolver);var request=new MockHttpServletRequest();request.setAttribute(AuthService.REQUEST_CONTEXT,new AuthSessionData(null,null,null,"user","User",Set.of("CAJERO"),Set.of("VENTAS_EDIT")));assertThrows(ForbiddenException.class,()->controller.configure("DATAICO",UUID.randomUUID(),null,request));verifyNoInteractions(service,resolver);}
}
