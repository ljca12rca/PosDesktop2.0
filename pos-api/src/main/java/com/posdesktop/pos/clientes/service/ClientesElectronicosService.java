package com.posdesktop.pos.clientes.service;

import com.posdesktop.pos.clientes.api.dto.ClienteElectronicoResponse;
import com.posdesktop.pos.clientes.api.dto.ConsultaDianResponse;
import com.posdesktop.pos.clientes.api.dto.RegistrarClienteElectronicoRequest;
import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.domain.TerceroDian;
import com.posdesktop.pos.modelo.relacional.ClienteElectronico;
import com.posdesktop.pos.repositorio.relacional.ClienteElectronicoRepositorio;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientesElectronicosService {
    private final ClienteElectronicoRepositorio clienteRepositorio;
    private final FacturacionElectronicaService facturacionElectronicaService;

    public ClientesElectronicosService(
            ClienteElectronicoRepositorio clienteRepositorio,
            FacturacionElectronicaService facturacionElectronicaService
    ) {
        this.clienteRepositorio = clienteRepositorio;
        this.facturacionElectronicaService = facturacionElectronicaService;
    }

    @Transactional
    public ClienteElectronicoResponse registrar(RegistrarClienteElectronicoRequest request) {
        String tipoIdentificacion = requerido(request.tipoIdentificacion()).toUpperCase(Locale.ROOT);
        String identificacion = requerido(request.identificacion());
        String tipoPersona = requerido(request.tipoPersona()).toUpperCase(Locale.ROOT);
        validarReglasDeNaturaleza(tipoPersona, tipoIdentificacion, identificacion, request);
        validarResidencia(request);

        ClienteElectronico cliente = clienteRepositorio.findByTipoIdentificacionAndIdentificacion(tipoIdentificacion, identificacion)
                .orElseGet(ClienteElectronico::new);
        aplicarDatos(cliente, request, tipoIdentificacion, identificacion, tipoPersona);
        return response(clienteRepositorio.saveAndFlush(cliente));
    }

    @Transactional
    public ClienteElectronicoResponse actualizar(UUID clienteId, RegistrarClienteElectronicoRequest request) {
        String tipoIdentificacion = requerido(request.tipoIdentificacion()).toUpperCase(Locale.ROOT);
        String identificacion = requerido(request.identificacion());
        String tipoPersona = requerido(request.tipoPersona()).toUpperCase(Locale.ROOT);
        validarReglasDeNaturaleza(tipoPersona, tipoIdentificacion, identificacion, request);
        validarResidencia(request);

        ClienteElectronico cliente = clienteRepositorio.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el cliente electrónico seleccionado."));
        clienteRepositorio.findByTipoIdentificacionAndIdentificacion(tipoIdentificacion, identificacion)
                .filter(encontrado -> !encontrado.getId().equals(clienteId))
                .ifPresent(encontrado -> {
                    throw new IllegalArgumentException("Ya existe otro cliente con ese tipo y número de identificación.");
                });
        aplicarDatos(cliente, request, tipoIdentificacion, identificacion, tipoPersona);
        return response(clienteRepositorio.saveAndFlush(cliente));
    }

    private void aplicarDatos(
            ClienteElectronico cliente,
            RegistrarClienteElectronicoRequest request,
            String tipoIdentificacion,
            String identificacion,
            String tipoPersona
    ) {
        cliente.setTipoIdentificacion(tipoIdentificacion);
        cliente.setTipoPersona(tipoPersona);
        cliente.setIdentificacion(identificacion);
        cliente.setDigitoVerificacion(opcional(request.digitoVerificacion()));
        cliente.setRazonSocial(nombreParaFactura(tipoPersona, request));
        cliente.setPrimerNombre(opcional(request.primerNombre()));
        cliente.setSegundoNombre(opcional(request.segundoNombre()));
        cliente.setPrimerApellido(opcional(request.primerApellido()));
        cliente.setSegundoApellido(opcional(request.segundoApellido()));
        cliente.setEmail(opcional(request.email()));
        cliente.setTelefono(opcional(request.telefono()));
        cliente.setDireccion(opcional(request.direccion()));
        cliente.setCiudadCodigoDane(opcional(request.ciudadCodigoDane()));
        cliente.setDepartamentoCodigoDane(opcional(request.departamentoCodigoDane()));
        String paisCodigo = opcional(request.paisCodigo());
        cliente.setPaisCodigo(paisCodigo == null ? "CO" : paisCodigo.toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<ClienteElectronicoResponse> buscar(String termino) {
        String busqueda = opcional(termino);
        if (busqueda == null || busqueda.length() < 2) {
            return List.of();
        }
        return clienteRepositorio.buscarParaVenta(busqueda).stream()
                .limit(12)
                .map(this::response)
                .toList();
    }

    public ConsultaDianResponse consultarDian(String tipoIdentificacion, String identificacion) {
        TerceroDian tercero = facturacionElectronicaService.consultarTerceroDian("dataico", tipoIdentificacion, identificacion);
        return consultaDianResponse(tercero);
    }

    public ConsultaDianResponse consultarDian(String identificacion) {
        return consultaDianResponse(facturacionElectronicaService.consultarTerceroDian("dataico", identificacion));
    }

    private ConsultaDianResponse consultaDianResponse(TerceroDian tercero) {
        return new ConsultaDianResponse(
                tercero.tipoPersona(), tercero.tipoIdentificacion(), tercero.identificacion(), tercero.digitoVerificacion(),
                tercero.razonSocial(), tercero.primerNombre(), tercero.segundoNombre(), tercero.primerApellido(),
                tercero.segundoApellido(), tercero.email(), tercero.telefono(), tercero.direccion(), tercero.ciudadCodigoDane(),
                tercero.departamentoCodigoDane(), tercero.paisCodigo()
        );
    }

    private ClienteElectronicoResponse response(ClienteElectronico cliente) {
        return new ClienteElectronicoResponse(cliente.getId().toString(), cliente.getTipoIdentificacion(),
                cliente.getTipoPersona(), cliente.getIdentificacion(), cliente.getDigitoVerificacion(),
                cliente.getRazonSocial(), cliente.getPrimerNombre(), cliente.getSegundoNombre(), cliente.getPrimerApellido(),
                cliente.getSegundoApellido(), cliente.getEmail(), cliente.getTelefono(), cliente.getDireccion(),
                cliente.getCiudadCodigoDane(), cliente.getDepartamentoCodigoDane(), cliente.getPaisCodigo());
    }

    private void validarReglasDeNaturaleza(
            String tipoPersona,
            String tipoIdentificacion,
            String identificacion,
            RegistrarClienteElectronicoRequest request
    ) {
        if (!"JURIDICA".equals(tipoPersona) && !"NATURAL".equals(tipoPersona)) {
            throw new IllegalArgumentException("Selecciona si el cliente es persona natural o jurídica.");
        }
        if ("JURIDICA".equals(tipoPersona)) {
            if (!"NIT".equals(tipoIdentificacion)) {
                throw new IllegalArgumentException("La persona jurídica debe identificarse con NIT.");
            }
            if (!identificacion.matches("\\d{5,15}")) {
                throw new IllegalArgumentException("El NIT debe contener entre 5 y 15 dígitos, sin el dígito de verificación.");
            }
            if (opcional(request.digitoVerificacion()) == null || !request.digitoVerificacion().trim().matches("\\d")) {
                throw new IllegalArgumentException("La persona jurídica requiere el dígito de verificación del NIT.");
            }
            if (opcional(request.razonSocial()) == null) {
                throw new IllegalArgumentException("La persona jurídica requiere la razón social registrada en el RUT.");
            }
            return;
        }
        if (opcional(request.primerNombre()) == null || opcional(request.primerApellido()) == null) {
            throw new IllegalArgumentException("La persona natural requiere primer nombre y primer apellido.");
        }
        if ("NIT".equals(tipoIdentificacion)
                && (opcional(request.digitoVerificacion()) == null || !request.digitoVerificacion().trim().matches("\\d"))) {
            throw new IllegalArgumentException("Una persona natural identificada con NIT requiere dígito de verificación.");
        }
    }

    private void validarResidencia(RegistrarClienteElectronicoRequest request) {
        if (opcional(request.direccion()) == null) {
            throw new IllegalArgumentException("La dirección de residencia es obligatoria para facturación electrónica.");
        }
        String pais = opcional(request.paisCodigo());
        if (pais == null || !pais.matches("(?i)[A-Z]{2}")) {
            throw new IllegalArgumentException("El país de residencia debe ser un código ISO de dos letras, por ejemplo CO.");
        }
        String departamento = opcional(request.departamentoCodigoDane());
        String ciudad = opcional(request.ciudadCodigoDane());
        if (departamento == null || ciudad == null) {
            throw new IllegalArgumentException("El departamento y la ciudad de residencia son obligatorios.");
        }
        if ("CO".equalsIgnoreCase(pais)) {
            if (!departamento.matches("\\d{2}")) {
                throw new IllegalArgumentException("Para Colombia, el departamento debe tener el código DANE de 2 dígitos.");
            }
            if (!ciudad.matches("\\d{3}")) {
                throw new IllegalArgumentException("Para Colombia, la ciudad debe tener el código DANE de 3 dígitos.");
            }
        }
    }

    private String nombreParaFactura(String tipoPersona, RegistrarClienteElectronicoRequest request) {
        if ("JURIDICA".equals(tipoPersona)) {
            return requerido(request.razonSocial());
        }
        return String.join(" ", java.util.stream.Stream.of(
                requerido(request.primerNombre()), opcional(request.segundoNombre()),
                requerido(request.primerApellido()), opcional(request.segundoApellido())
        ).filter(value -> value != null && !value.isBlank()).toList());
    }

    private String requerido(String value) { return value == null ? "" : value.trim(); }
    private String opcional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
