package com.posdesktop.pos.facturacion.electronica.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataicoConfiguracionInicializador implements ApplicationRunner {
    private final DataicoConfiguracionService configuracion;
    public DataicoConfiguracionInicializador(DataicoConfiguracionService configuracion) { this.configuracion = configuracion; }
    @Override public void run(ApplicationArguments args) { configuracion.inicializar(); }
}
