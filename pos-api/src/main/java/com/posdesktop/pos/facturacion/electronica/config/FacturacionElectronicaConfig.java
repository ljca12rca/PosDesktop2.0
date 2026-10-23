package com.posdesktop.pos.facturacion.electronica.config;

import com.posdesktop.pos.facturacion.electronica.application.ProveedorFacturacionElectronica;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoFacturacionElectronicaProvider;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoInvoiceMapper;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DataicoProperties.class)
public class FacturacionElectronicaConfig {
    @Bean
    ProveedorFacturacionElectronica dataicoProvider(DataicoProperties properties, DataicoInvoiceMapper mapper,
            DataicoConfiguracionService configuracion) {
        return new DataicoFacturacionElectronicaProvider(mapper, configuracion::paraProveedor, current -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(orDefault(current.connectTimeout(), Duration.ofSeconds(5)));
            requestFactory.setReadTimeout(orDefault(current.readTimeout(), Duration.ofSeconds(30)));
            return RestClient.builder().baseUrl(DataicoConfiguracionService.validarUrl(baseUrl(current))).requestFactory(requestFactory).build();
        });
    }

    private String baseUrl(DataicoProperties properties) {
        return properties.baseUrl() == null || properties.baseUrl().isBlank()
                ? DataicoProperties.DEFAULT_BASE_URL : properties.baseUrl();
    }

    private Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

}
