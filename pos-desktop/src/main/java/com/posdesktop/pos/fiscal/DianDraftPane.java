package com.posdesktop.pos.fiscal;

import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.PosApiClient;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Clasificacion explicita: no cambia importes de la venta ni infiere exencion de impuesto cero. */
public final class DianDraftPane extends VBox {
    private final PosApiClient api;
    private final PosApiClient.VentaRegistradaResponse sale;
    private final Label status=note("Cargando configuración de pruebas…");
    private final ComboBox<Choice> environment=choices("PRUEBAS","Pruebas / habilitación","PRODUCCION","Producción · previsualización");
    private final TextField number=field("dian-draft-number","Número para previsualizar"),duties=field("dian-customer-duties","Responsabilidades del RUT, separadas por ;");
    private final ComboBox<Choice> tax=choices("01","IVA","04","INC","ZA","IVA e INC","ZZ","No aplica");
    private final ComboBox<Choice> paymentForm=choices("1","Contado","2","Crédito");
    private final ComboBox<Choice> paymentMeans=choices("10","Efectivo","48","Tarjeta crédito","49","Tarjeta débito","42","Consignación");
    private final ComboBox<Choice> unit=choices("94","Unidad (94)","NIU","Número de unidades (NIU)");
    private final DatePicker dueDate=new DatePicker();
    private final Map<Integer,ComboBox<Choice>> treatments=new LinkedHashMap<>();
    private final SimpleBooleanProperty busy=new SimpleBooleanProperty();
    private PosApiClient.DianPreparation preparation;
    private String customerId;
    private int searchRevision;
    private final Consumer<PosApiClient.DianDraftResult> success;
    public DianDraftPane(PosApiClient api,PosApiClient.VentaRegistradaResponse sale,String customerId,String customerName,Consumer<PosApiClient.DianDraftResult> success) {
        super(14);this.api=api;this.sale=sale;this.customerId=customerId;this.success=success;setId("dian-draft-pane");setMinHeight(Region.USE_PREF_SIZE);
        TextField search=field("dian-draft-customer-search","Nombre o identificación del cliente");search.setText(customerName);
        ComboBox<Choice> matches=new ComboBox<>();matches.setPromptText("Selecciona un cliente existente");matches.setId("dian-draft-customer-matches");
        if(customerId!=null){matches.getItems().add(new Choice(customerId,customerName));matches.getSelectionModel().selectFirst();}
        Label selectedCustomer=note(customerId==null?"Selecciona un cliente existente.":"Cliente seleccionado: "+customerName);
        search.textProperty().addListener((o,a,b)->{this.customerId=null;searchRevision++;matches.getItems().clear();selectedCustomer.setText("Selecciona un cliente existente.");});
        search.setOnAction(e->{int revision=++searchRevision;status(status,"Buscando clientes…",false);
            async(()->api.buscarClientesElectronicos(search.getText().trim()),values->{if(revision!=searchRevision)return;matches.getItems().setAll(values.stream().map(c->new Choice(c.id(),c.nombreFactura()+" · "+c.identificacion())).toList());if(!values.isEmpty()) {matches.getSelectionModel().selectFirst();matches.show();} else status(status,"No hay clientes coincidentes en la base de datos.",false);},this::failure);});
        matches.setOnAction(e->{if(matches.getValue()!=null){this.customerId=matches.getValue().code();selectedCustomer.setText("Cliente seleccionado: "+matches.getValue().label());}});
        environment.setId("dian-draft-environment");environment.disableProperty().bind(busy);
        tax.setId("dian-customer-tax");paymentForm.setId("dian-payment-form");paymentMeans.setId("dian-payment-means");unit.setId("dian-line-unit");dueDate.setId("dian-due-date");
        dueDate.setValue(sale.fechaVenta().toLocalDate());paymentForm.setOnAction(e->{if(paymentForm.getValue()!=null&&"1".equals(paymentForm.getValue().code()))dueDate.setValue(sale.fechaVenta().toLocalDate());});
        VBox fields=card("Borrador de la venta "+sale.numeroVenta(),note("Este XML se guardará sin firma ni envío. No reserva consecutivos y no cambia la venta local."),
                row(group("Ambiente",environment,250),group("Número sin prefijo *",number,220)),
                row(group("Buscar cliente (Enter)",search,330),group("Cliente existente *",matches,370)),selectedCustomer,
                row(group("Responsabilidades del cliente *",duties,330),group("Régimen del cliente *",tax,240)),
                row(group("Forma de pago *",paymentForm,180),group("Medio de pago *",paymentMeans,220),group("Vencimiento *",dueDate,210),group("Unidad de los artículos *",unit,220)));
        VBox lines=new VBox(10);lines.getChildren().add(note("Selecciona el tratamiento de cada artículo. Los impuestos y totales del XML deben coincidir con los guardados en la venta."));
        for(var detail:sale.detalles()) {
            ComboBox<Choice> treatment=choices("IVA19","IVA 19 %","IVA5","IVA 5 %","EXEMPT","Exento · IVA 0 %","EXCLUDED","Excluido · sin IVA");
            treatment.setId("dian-line-treatment-"+detail.orden());treatments.put(detail.orden(),treatment);
            Label description=new Label(detail.orden()+". "+detail.descripcion()+" · Cantidad "+detail.cantidad()+" · Total $ "+detail.total());description.setWrapText(true);description.setPrefWidth(490);
            lines.getChildren().add(row(description,group("Tratamiento tributario *",treatment,240)));
        }
        Button generate=button("Generar y guardar borrador",true);generate.setId("dian-draft-generate");generate.disableProperty().bind(busy);
        generate.setOnAction(e->{try {
            if(preparation==null||this.customerId==null||dueDate.getValue()==null)throw new IllegalArgumentException("Selecciona un cliente y completa la configuración y el vencimiento.");
            List<PosApiClient.DianLineClassification> classified=new ArrayList<>();
            String unitCode=selected(unit);
            for(var entry:treatments.entrySet()) {
                String choice=selected(entry.getValue());String treatment=choice.startsWith("IVA")?"TAXED":choice;
                BigDecimal percent=switch(choice){case "IVA19"->new BigDecimal("19");case "IVA5"->new BigDecimal("5");default->BigDecimal.ZERO;};
                classified.add(new PosApiClient.DianLineClassification(entry.getKey(),unitCode,List.of(new PosApiClient.DianTax(treatment,"01","IVA",percent,null))));
            }
            var request=new PosApiClient.DianDraftRequest(this.customerId,selected(environment),preparation.configurationVersion(),Long.parseLong(number.getText().trim()),duties.getText().trim(),selected(tax),selected(paymentForm),selected(paymentMeans),dueDate.getValue(),classified,List.of(),List.of());
            busy.set(true);fields.setDisable(true);lines.setDisable(true);status(status,"Generando y guardando evidencia local…",false);
            async(()->api.generarBorradorDian(sale.id(),request),r->{busy.set(false);fields.setDisable(false);lines.setDisable(false);status(status,"Borrador guardado. Sin firma ni envío DIAN.",false);success.accept(r);},error->{fields.setDisable(false);lines.setDisable(false);failure(error);});
        }catch(RuntimeException error){status(status,error instanceof NumberFormatException?"Ingresa un número válido sin prefijo.":error.getMessage(),true);}});
        status.setId("dian-draft-status");getChildren().addAll(fields,card("Artículos y clasificación",lines),status,row(generate));environment.getSelectionModel().selectFirst();environment.setOnAction(e->load());load();
    }
    private void load(){preparation=null;number.clear();busy.set(true);status(status,"Cargando configuración del ambiente…",false);
        async(()->api.prepararBorradorDian(selected(environment)),p->{preparation=p;number.setText(Long.toString(p.previewNumber()));busy.set(false);
            status(status,"Emisor: "+p.issuerName()+" · Prefijo "+p.prefix()+" · "+(p.secretsConfigured()?"Credenciales configuradas.":"Faltan clave técnica o PIN."),!p.secretsConfigured());},this::failure);}
    private void failure(RuntimeException error){busy.set(false);status(status,"No se pudo completar: "+error.getMessage(),true);}
}
