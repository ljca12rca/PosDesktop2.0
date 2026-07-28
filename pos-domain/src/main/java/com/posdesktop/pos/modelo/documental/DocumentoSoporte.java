package com.posdesktop.pos.modelo.documental;

import com.posdesktop.pos.modelo.enumeraciones.EntidadOrigenDocumento;
import com.posdesktop.pos.modelo.enumeraciones.TipoDocumentoSoporte;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "documentos_soporte")
public class DocumentoSoporte {

    @Id
    private String id;

    private EntidadOrigenDocumento entidadOrigen;
    private String entidadOrigenId;
    private TipoDocumentoSoporte tipoDocumento;
    private String nombreArchivo;
    private String contentType;
    private Long tamanioBytes;
    private String gridFsFileId;
    private String rutaArchivo;
    private String rutaRelativa;
    private List<String> carpetas = new ArrayList<>();
    private String checksum;
    private String observacion;
    private LocalDateTime cargadoEn = LocalDateTime.now();

    public String getId() {
        return id;
    }

    public EntidadOrigenDocumento getEntidadOrigen() {
        return entidadOrigen;
    }

    public void setEntidadOrigen(EntidadOrigenDocumento entidadOrigen) {
        this.entidadOrigen = entidadOrigen;
    }

    public String getEntidadOrigenId() {
        return entidadOrigenId;
    }

    public void setEntidadOrigenId(String entidadOrigenId) {
        this.entidadOrigenId = entidadOrigenId;
    }

    public TipoDocumentoSoporte getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumentoSoporte tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getTamanioBytes() {
        return tamanioBytes;
    }

    public void setTamanioBytes(Long tamanioBytes) {
        this.tamanioBytes = tamanioBytes;
    }

    public String getGridFsFileId() {
        return gridFsFileId;
    }

    public void setGridFsFileId(String gridFsFileId) {
        this.gridFsFileId = gridFsFileId;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public String getRutaRelativa() {
        return rutaRelativa;
    }

    public void setRutaRelativa(String rutaRelativa) {
        this.rutaRelativa = rutaRelativa;
    }

    public List<String> getCarpetas() {
        return carpetas;
    }

    public void setCarpetas(List<String> carpetas) {
        this.carpetas = carpetas == null ? new ArrayList<>() : new ArrayList<>(carpetas);
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public LocalDateTime getCargadoEn() {
        return cargadoEn;
    }
}
