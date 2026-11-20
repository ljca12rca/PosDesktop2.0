package com.posdesktop.pos.facturacion.electronica.dian.transport;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalConflictException;
import com.posdesktop.pos.repositorio.relacional.DianTransportSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class DianTransportSettingsService {
    private final DianTransportSettingsRepository settings;
    public DianTransportSettingsService(DianTransportSettingsRepository settings){this.settings=settings;}
    public record Settings(String mode,long version,String explanation){ }
    public record Save(String mode,long version){ }
    @Transactional(readOnly=true) public Settings get(){var c=settings.findById(1L).orElseThrow();return response(c.getMode(),c.getVersion());}
    @Transactional public Settings save(Save r){if(r==null||!Set.of("SIMULACION","HABILITACION").contains(r.mode()==null?"":r.mode()))throw new IllegalArgumentException("Modo de transporte invalido.");var c=settings.lock().orElseThrow();if(c.getVersion()!=r.version())throw new FiscalConflictException("El modo de transporte cambio. Recarga antes de guardar.");c.setMode(r.mode());settings.saveAndFlush(c);return response(c.getMode(),c.getVersion());}
    private Settings response(String mode,long version){return new Settings(mode,version,"SIMULACION".equals(mode)?"Prueba local sin conexion con DIAN. Sus resultados no acreditan aceptacion ni habilitacion.":"Destino oficial de habilitacion. El envio requiere verificar el certificado real; la firma local de prueba no habilita solicitudes reales.");}
}
