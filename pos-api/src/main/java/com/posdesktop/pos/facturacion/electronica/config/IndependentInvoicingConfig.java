package com.posdesktop.pos.facturacion.electronica.config;

import com.posdesktop.pos.invoicing.application.ElectronicProvider;
import com.posdesktop.pos.invoicing.application.ProviderRegistry;
import com.posdesktop.pos.invoicing.infrastructure.xml.UblDraftGenerator;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IndependentInvoicingConfig {
    @Bean UblDraftGenerator ublDraftGenerator() { return new UblDraftGenerator(); }
    @Bean DianInvoiceDraftGenerator dianInvoiceDraftGenerator() { return new DianInvoiceDraftGenerator(); }
    @Bean ProviderRegistry independentProviderRegistry(List<ElectronicProvider> providers) {
        return new ProviderRegistry(providers);
    }
}
