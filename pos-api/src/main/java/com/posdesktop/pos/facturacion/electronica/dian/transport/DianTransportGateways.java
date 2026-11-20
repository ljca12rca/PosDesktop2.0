package com.posdesktop.pos.facturacion.electronica.dian.transport;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class DianTransportGateways {
    private final Map<String,DianTransportGateway> gateways;
    public DianTransportGateways(List<DianTransportGateway> gateways){var map=new HashMap<String,DianTransportGateway>();for(var g:gateways)if(map.put(g.mode(),g)!=null)throw new IllegalArgumentException("Modo de transporte duplicado.");this.gateways=Map.copyOf(map);}
    public DianTransportGateway get(String mode){var gateway=gateways.get(mode);if(gateway==null)throw new IllegalArgumentException("Transporte no disponible para este modo.");return gateway;}
}
