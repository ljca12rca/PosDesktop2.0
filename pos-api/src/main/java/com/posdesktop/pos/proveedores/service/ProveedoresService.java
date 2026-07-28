package com.posdesktop.pos.proveedores.service;

import com.posdesktop.pos.modelo.relacional.FacturaProveedor;
import com.posdesktop.pos.modelo.relacional.Proveedor;
import com.posdesktop.pos.proveedores.api.dto.ProveedorResponse;
import com.posdesktop.pos.proveedores.api.dto.RegistrarProveedorRequest;
import com.posdesktop.pos.repositorio.relacional.FacturaProveedorRepositorio;
import com.posdesktop.pos.repositorio.relacional.ProveedorRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProveedoresService {

    private final ProveedorRepositorio proveedorRepositorio;
    private final FacturaProveedorRepositorio facturaProveedorRepositorio;

    public ProveedoresService(
            ProveedorRepositorio proveedorRepositorio,
            FacturaProveedorRepositorio facturaProveedorRepositorio
    ) {
        this.proveedorRepositorio = proveedorRepositorio;
        this.facturaProveedorRepositorio = facturaProveedorRepositorio;
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listarProveedores() {
        List<Proveedor> proveedores = proveedorRepositorio.findAllByOrderByNombreAsc();
        Map<UUID, List<FacturaProveedor>> facturasPorProveedor = facturaProveedorRepositorio.findAll().stream()
                .peek(FacturaProveedor::recalcularSaldo)
                .collect(Collectors.groupingBy(factura -> factura.getProveedor().getId()));

        return proveedores.stream()
                .map(proveedor -> mapearProveedor(proveedor, facturasPorProveedor.getOrDefault(proveedor.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ProveedorResponse registrarProveedor(RegistrarProveedorRequest request) {
        String nit = limpiarRequerido(request.nit());
        proveedorRepositorio.findByNitIgnoreCase(nit)
                .ifPresent(proveedor -> {
                    throw new IllegalArgumentException("Ya existe un proveedor registrado con NIT " + nit + ".");
                });

        Proveedor proveedor = new Proveedor();
        proveedor.setNit(nit);
        proveedor.setNombre(limpiarRequerido(request.nombre()));
        proveedor.setTelefono(limpiarOpcional(request.telefono()));
        proveedor.setEmail(limpiarOpcional(request.correo()));
        proveedor.setDireccion(limpiarOpcional(request.direccion()));
        proveedor.setObservacion(limpiarOpcional(request.observacion()));

        Proveedor guardado = proveedorRepositorio.saveAndFlush(proveedor);
        return mapearProveedor(guardado, List.of());
    }

    private ProveedorResponse mapearProveedor(Proveedor proveedor, List<FacturaProveedor> facturas) {
        BigDecimal saldoPendiente = facturas.stream()
                .map(FacturaProveedor::getSaldoPendiente)
                .map(this::normalizarDinero)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new ProveedorResponse(
                proveedor.getId().toString(),
                proveedor.getNit(),
                proveedor.getNombre(),
                proveedor.getTelefono(),
                proveedor.getEmail(),
                proveedor.getDireccion(),
                proveedor.getObservacion(),
                proveedor.isActivo(),
                saldoPendiente,
                facturas.size()
        );
    }

    private BigDecimal normalizarDinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String limpiarRequerido(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String limpiarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
