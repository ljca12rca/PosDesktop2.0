package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.facturacion.electronica.integration.SaleFiscalSnapshotMapper;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalDraftService {
    private final DianSettingsRepository settings;
    private final VentaRepositorio sales;
    private final ClienteElectronicoRepositorio customers;
    private final FiscalDocumentRepository documents;
    private final FiscalEventRepository events;
    private final FiscalSecretVault vault;
    private final FiscalJson json;
    private final SaleFiscalSnapshotMapper mapper;
    private final DianInvoiceDraftGenerator generator;
    public FiscalDraftService(DianSettingsRepository settings, VentaRepositorio sales, ClienteElectronicoRepositorio customers,
            FiscalDocumentRepository documents, FiscalEventRepository events, FiscalSecretVault vault, FiscalJson json,
            SaleFiscalSnapshotMapper mapper, DianInvoiceDraftGenerator generator) {
        this.settings=settings; this.sales=sales; this.customers=customers; this.documents=documents; this.events=events;
        this.vault=vault; this.json=json; this.mapper=mapper; this.generator=generator;
    }
    public record LineClassification(int order, @NotBlank String unitCode, @NotEmpty List<FiscalInvoice.Tax> taxes) { }
    public record Request(@NotNull UUID customerId, @NotBlank String environment, long configurationVersion, @Positive long number,
            @NotBlank String customerResponsibilities, @NotBlank String customerTaxScheme, @NotBlank String paymentForm,
            @NotBlank String paymentMeans, @NotNull LocalDate dueDate, @NotEmpty @Size(max=500) List<LineClassification> lines,
            List<FiscalInvoice.Adjustment> adjustments, List<FiscalInvoice.Withholding> withholdings) { }
    public record Preparation(String environment, long configurationVersion, String prefix, long previewNumber,
            LocalDate validFrom, LocalDate validUntil, String issuerName, boolean secretsConfigured) { }
    public record Snapshot(int schemaVersion, FiscalInvoice invoice, DianProfile profile, long configurationVersion) { }
    public record Result(UUID id, String number, String state, String xml, String cufe, String hash, boolean readyToTransmit) { }

    @Transactional(readOnly=true)
    public Preparation preparation(String environment) {
        var row = settings.findById(FiscalSettingsService.environment(environment)).orElseThrow();
        if (row.getDatosJson() == null) throw new IllegalArgumentException("Un administrador debe configurar DIAN para este ambiente.");
        var config = json.read(row.getDatosJson(),DianConfiguration.class);
        var secrets = vault.decrypt(row.getSecretosCifrados(),environment);
        return new Preparation(environment,row.getVersion(),config.numbering().prefix(),config.previewNumber(),config.numbering().validFrom(),
                config.numbering().validUntil(),config.issuer().name(),secrets.technicalKey() != null && secrets.softwarePin() != null);
    }
    @Transactional
    public Result generate(UUID saleId, Request request) {
        String environment = FiscalSettingsService.environment(request.environment());
        var row = settings.lockSettings(environment).orElseThrow();
        if (row.getDatosJson() == null) throw new IllegalArgumentException("Configura DIAN antes de generar un borrador.");
        if (row.getVersion() != request.configurationVersion()) throw new FiscalConflictException("La configuracion DIAN cambio. Recarga antes de generar el borrador.");
        var config = json.read(row.getDatosJson(),DianConfiguration.class);
        var sale = sales.findForFiscalUpdate(saleId).orElseThrow(() -> new ResourceNotFoundException("No existe la venta."));
        var customer = customers.findById(request.customerId()).orElseThrow(() -> new ResourceNotFoundException("No existe el cliente."));
        var catalogs = new DianCatalogs();
        String department = customer.getDepartamentoCodigoDane();
        String city = customer.getCiudadCodigoDane();
        if (city != null && city.matches("[0-9]{3}") && department != null) city = department + city;
        String identification = switch (customer.getTipoIdentificacion()) {
            case "CC" -> "13"; case "NIT" -> "31"; case "CE" -> "22"; case "TI" -> "12";
            case "PP" -> "41"; case "PEP" -> "47"; case "PPT" -> "48"; default -> customer.getTipoIdentificacion();
        };
        String organization;
        if ("NATURAL".equals(customer.getTipoPersona())) organization = "2";
        else if ("JURIDICA".equals(customer.getTipoPersona())) organization = "1";
        else throw new IllegalArgumentException("El cliente debe indicar naturaleza natural o juridica.");
        var party = new FiscalInvoice.Party(identification,customer.getIdentificacion(),customer.getDigitoVerificacion(),DataicoSalePayload.customerName(customer),
                request.customerResponsibilities(),request.customerTaxScheme(),catalogs.require("party-tax",request.customerTaxScheme()),
                new FiscalInvoice.Address(customer.getDireccion(),city,catalogs.require("municipality",city),department,catalogs.require("department",department),customer.getPaisCodigo()));
        Map<Integer,SaleFiscalSnapshotMapper.Classification> classifications = new HashMap<>();
        if (request.lines().size() != sale.getDetalles().size()) throw new IllegalArgumentException("Clasifica todos los articulos de la venta una sola vez.");
        for (var line : request.lines()) {
            if (line.unitCode() == null || line.taxes() == null || classifications.put(line.order(),new SaleFiscalSnapshotMapper.Classification(line.unitCode(),line.taxes())) != null)
                throw new IllegalArgumentException("Hay clasificaciones de articulo incompletas o duplicadas.");
        }
        var context = new SaleFiscalSnapshotMapper.Context(config.numbering().prefix() + request.number(),config.issuer(),party,config.numbering(),
                new FiscalInvoice.Payment(request.paymentMeans(),request.dueDate(),"VENTA-" + sale.getNumeroVenta()),classifications,
                request.adjustments() == null ? List.of() : request.adjustments(),request.withholdings() == null ? List.of() : request.withholdings());
        var invoice = mapper.snapshot(sale,context);
        var profile = config.profile(environment,organization,request.paymentForm());
        var storedSecrets = vault.decrypt(row.getSecretosCifrados(),environment);
        if (storedSecrets.technicalKey() == null || storedSecrets.softwarePin() == null) throw new IllegalArgumentException("Configura la clave tecnica y el PIN de software para este ambiente.");
        char[] key=storedSecrets.technicalKey().toCharArray(),pin=storedSecrets.softwarePin().toCharArray();
        DianInvoiceDraftGenerator.Draft draft;
        try (var secrets = new DianSecrets(key,pin)) { draft = generator.generate(invoice,profile,secrets); }
        finally { Arrays.fill(key,'\0'); Arrays.fill(pin,'\0'); }
        String snapshot = json.write(new Snapshot(1,invoice,profile,row.getVersion()));
        String hash = json.hash(snapshot + "\n" + draft.xml());
        var existing = documents.findByHuella(hash).orElse(null);
        if (existing != null) return new Result(existing.getId(),existing.getNumero(),"BORRADOR_SIN_FIRMA",existing.getXml(),draft.cufe(),existing.getHuella(),false);
        var document = documents.saveAndFlush(new FiscalDocumentRecord(saleId,request.customerId(),"BORRADOR","DIAN_DIRECT",environment,
                config.issuer().identification(),invoice.number(),"DIAN_INVOICE_V1",snapshot,draft.xml(),hash,party.name(),invoice.declaredPayable()));
        events.save(new FiscalEvent(document.getId(),"BORRADOR_GENERADO","XML sin firma. No reserva consecutivos ni acredita emision o aceptacion DIAN."));
        return new Result(document.getId(),document.getNumero(),"BORRADOR_SIN_FIRMA",draft.xml(),draft.cufe(),hash,false);
    }
}
