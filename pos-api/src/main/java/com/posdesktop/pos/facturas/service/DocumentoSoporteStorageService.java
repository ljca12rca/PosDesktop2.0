package com.posdesktop.pos.facturas.service;

import com.posdesktop.pos.modelo.documental.DocumentoSoporte;
import com.posdesktop.pos.modelo.enumeraciones.EntidadOrigenDocumento;
import com.posdesktop.pos.modelo.enumeraciones.TipoDocumentoSoporte;
import com.posdesktop.pos.modelo.relacional.FacturaProveedor;
import com.posdesktop.pos.modelo.relacional.PagoFactura;
import com.posdesktop.pos.repositorio.documental.DocumentoSoporteRepositorio;
import com.posdesktop.pos.shared.config.DocumentoStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentoSoporteStorageService {

    private static final List<String> CONTENT_TYPES_PERMITIDOS = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final DocumentoSoporteRepositorio documentoSoporteRepositorio;
    private final DocumentoStorageProperties properties;

    public DocumentoSoporteStorageService(
            DocumentoSoporteRepositorio documentoSoporteRepositorio,
            DocumentoStorageProperties properties
    ) {
        this.documentoSoporteRepositorio = documentoSoporteRepositorio;
        this.properties = properties;
    }

    public List<DocumentoSoporte> guardarSoportesFactura(FacturaProveedor factura, List<MultipartFile> archivos) {
        if (factura == null) {
            throw new IllegalArgumentException("La factura es obligatoria para guardar soportes.");
        }
        return guardarArchivos(
                factura.getProveedor().getNit(),
                factura.getNumeroFactura(),
                List.of("proveedores", normalizarToken(factura.getProveedor().getNit()), "facturas", normalizarToken(factura.getNumeroFactura()), "imagenes"),
                EntidadOrigenDocumento.FACTURA_PROVEEDOR,
                factura.getId().toString(),
                TipoDocumentoSoporte.IMAGEN_FACTURA,
                archivos,
                "Soporte de factura proveedor"
        );
    }

    public List<DocumentoSoporte> guardarSoportesPago(
            FacturaProveedor factura,
            PagoFactura pago,
            List<MultipartFile> archivos
    ) {
        if (factura == null || pago == null) {
            throw new IllegalArgumentException("La factura y el pago son obligatorios para guardar el soporte del abono.");
        }
        return guardarArchivos(
                factura.getProveedor().getNit(),
                factura.getNumeroFactura(),
                List.of(
                        "proveedores",
                        normalizarToken(factura.getProveedor().getNit()),
                        "facturas",
                        normalizarToken(factura.getNumeroFactura()),
                        "abonos",
                        pago.getId().toString()
                ),
                EntidadOrigenDocumento.PAGO_FACTURA,
                pago.getId().toString(),
                TipoDocumentoSoporte.COMPROBANTE_PAGO,
                archivos,
                "Comprobante de abono"
        );
    }

    private List<DocumentoSoporte> guardarArchivos(
            String nitProveedor,
            String numeroFactura,
            List<String> carpetas,
            EntidadOrigenDocumento entidadOrigen,
            String entidadOrigenId,
            TipoDocumentoSoporte tipoDocumento,
            List<MultipartFile> archivos,
            String observacionBase
    ) {
        List<MultipartFile> archivosValidos = normalizarArchivos(archivos);
        if (archivosValidos.isEmpty()) {
            return List.of();
        }

        Path storageRoot = resolverStorageRoot();
        Path directorioDestino = construirDirectorio(storageRoot, carpetas);
        List<Path> archivosGuardados = new ArrayList<>();

        try {
            Files.createDirectories(directorioDestino);
            List<DocumentoSoporte> documentos = new ArrayList<>();
            for (MultipartFile archivo : archivosValidos) {
                validarArchivo(archivo);
                String nombreOriginal = resolverNombreOriginal(archivo);
                String nombreFisico = construirNombreFisico(nombreOriginal);
                Path destino = directorioDestino.resolve(nombreFisico);
                byte[] contenido = archivo.getBytes();
                Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);
                archivosGuardados.add(destino);

                DocumentoSoporte documento = new DocumentoSoporte();
                documento.setEntidadOrigen(entidadOrigen);
                documento.setEntidadOrigenId(entidadOrigenId);
                documento.setTipoDocumento(tipoDocumento);
                documento.setNombreArchivo(nombreOriginal);
                documento.setContentType(archivo.getContentType());
                documento.setTamanioBytes((long) contenido.length);
                documento.setRutaArchivo(destino.toAbsolutePath().toString());
                documento.setRutaRelativa(storageRoot.relativize(destino).toString());
                documento.setCarpetas(carpetas);
                documento.setChecksum(calcularChecksum(contenido));
                documento.setObservacion(observacionBase + " proveedor " + nitProveedor + " factura " + numeroFactura + ".");
                documentos.add(documento);
            }
            return documentoSoporteRepositorio.saveAll(documentos);
        } catch (IOException exception) {
            limpiarArchivosGuardados(archivosGuardados);
            throw new IllegalStateException("No fue posible guardar los soportes en disco.", exception);
        } catch (RuntimeException exception) {
            limpiarArchivosGuardados(archivosGuardados);
            throw exception;
        }
    }

    private List<MultipartFile> normalizarArchivos(List<MultipartFile> archivos) {
        if (archivos == null) {
            return List.of();
        }
        return archivos.stream()
                .filter(archivo -> archivo != null && !archivo.isEmpty())
                .toList();
    }

    private Path resolverStorageRoot() {
        String rootConfigurado = properties.storageRoot();
        if (rootConfigurado == null || rootConfigurado.isBlank()) {
            return Path.of(System.getProperty("user.home"), "posdesktop-media");
        }
        return Path.of(rootConfigurado);
    }

    private Path construirDirectorio(Path root, List<String> carpetas) {
        Path actual = root;
        for (String carpeta : carpetas) {
            actual = actual.resolve(carpeta);
        }
        return actual;
    }

    private void validarArchivo(MultipartFile archivo) {
        String contentType = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException("Solo se permiten imagenes JPG, PNG, WEBP o archivos PDF como soporte.");
        }
    }

    private String resolverNombreOriginal(MultipartFile archivo) {
        String original = archivo.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "archivo-" + UUID.randomUUID();
        }
        return original.trim();
    }

    private String construirNombreFisico(String nombreOriginal) {
        String extension = "";
        int indiceExtension = nombreOriginal.lastIndexOf('.');
        if (indiceExtension >= 0) {
            extension = nombreOriginal.substring(indiceExtension);
            nombreOriginal = nombreOriginal.substring(0, indiceExtension);
        }
        return normalizarToken(nombreOriginal) + "-" + UUID.randomUUID() + extension.toLowerCase(Locale.ROOT);
    }

    private String normalizarToken(String valor) {
        String normalizado = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        normalizado = normalizado.replaceAll("[^a-z0-9]+", "-");
        normalizado = normalizado.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalizado.isBlank() ? "archivo" : normalizado;
    }

    private String calcularChecksum(byte[] contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No fue posible calcular el checksum del archivo.", exception);
        }
    }

    private void limpiarArchivosGuardados(List<Path> archivosGuardados) {
        for (Path archivo : archivosGuardados) {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException ignored) {
                // Si la eliminacion falla, la metadata no se persiste y el archivo queda para revision manual.
            }
        }
    }
}
