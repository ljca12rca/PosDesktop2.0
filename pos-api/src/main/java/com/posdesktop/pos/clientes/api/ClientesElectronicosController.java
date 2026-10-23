package com.posdesktop.pos.clientes.api;

import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.clientes.api.dto.ClienteElectronicoResponse;
import com.posdesktop.pos.clientes.api.dto.ConsultaDianResponse;
import com.posdesktop.pos.clientes.api.dto.RegistrarClienteElectronicoRequest;
import com.posdesktop.pos.clientes.service.ClientesElectronicosService;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CLIENTES_ELECTRONICOS)
public class ClientesElectronicosController {
    private final ClientesElectronicosService clientesService;
    public ClientesElectronicosController(ClientesElectronicosService clientesService) { this.clientesService = clientesService; }

    @GetMapping
    @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<ClienteElectronicoResponse>> buscar(
            @RequestParam(name = "q") String termino,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success("Clientes electrónicos consultados correctamente.", httpRequest.getRequestURI(), clientesService.buscar(termino));
    }

    @GetMapping("/consulta-dian")
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<ConsultaDianResponse> consultarDian(
            @RequestParam(required = false) String tipoIdentificacion,
            @RequestParam String identificacion,
            HttpServletRequest httpRequest
    ) {
        ConsultaDianResponse consulta = tipoIdentificacion == null || tipoIdentificacion.isBlank()
                ? clientesService.consultarDian(identificacion)
                : clientesService.consultarDian(tipoIdentificacion, identificacion);
        return ApiResponse.success(
                "Consulta DIAN realizada correctamente.", httpRequest.getRequestURI(),
                consulta
        );
    }

    @PostMapping
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<ClienteElectronicoResponse> registrar(
            @Valid @RequestBody RegistrarClienteElectronicoRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success("Cliente electrónico registrado correctamente.", httpRequest.getRequestURI(), clientesService.registrar(request));
    }

    @PutMapping("/{clienteId}")
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<ClienteElectronicoResponse> actualizar(
            @PathVariable UUID clienteId,
            @Valid @RequestBody RegistrarClienteElectronicoRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Cliente electrónico actualizado correctamente.",
                httpRequest.getRequestURI(),
                clientesService.actualizar(clienteId, request)
        );
    }
}
