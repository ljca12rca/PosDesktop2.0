package com.posdesktop.pos.fiscal;

import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.PosApiClient;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.*;
import java.util.*;

/** Configuracion nueva sin reetiquetar la pantalla ni las credenciales Dataico existentes. */
public final class FiscalSettingsPane extends VBox {
    private final PosApiClient api;
    private final Label status=note("Cargando configuración…"),secretState=note(""),providerState=note("");
    private final ComboBox<Choice> provider=choices("DATAICO","Dataico","DIAN_DIRECT","DIAN directa · solo borradores");
    private final ComboBox<Choice> environment=choices("PRUEBAS","Pruebas / habilitación","PRODUCCION","Producción");
    private final ComboBox<Choice> nature=choices("1","Persona jurídica","2","Persona natural");
    private final ComboBox<Choice> tax=choices("01","IVA","04","INC","ZA","IVA e INC","ZZ","No aplica");
    private final ComboBox<Choice> department=new ComboBox<>();
    private final ComboBox<City> city=new ComboBox<>();
    private final Map<String,TextField> fields=new LinkedHashMap<>();
    private final PasswordField technicalKey=secret("dian-technical-key"),pin=secret("dian-software-pin"),password=secret("dian-certificate-password");
    private final DatePicker validFrom=new DatePicker(),validUntil=new DatePicker();
    private final BooleanProperty busy=new SimpleBooleanProperty();
    private final BooleanProperty loadedReady=new SimpleBooleanProperty(),providerReady=new SimpleBooleanProperty(),providerBusy=new SimpleBooleanProperty();
    private final Button save=button("Guardar configuración",true),reload=button("Recargar",false);
    private PosApiClient.DianSettingsResponse loaded;
    private PosApiClient.FiscalProviderResponse loadedProvider;
    private Path certificate;
    private final Label certificateLabel=note("No se seleccionó un archivo nuevo.");
    private final List<City> cities=FiscalUi.cities();

    public FiscalSettingsPane(PosApiClient api) {
        super(18); this.api=api; setId("fiscal-settings-pane"); setMinHeight(Region.USE_PREF_SIZE);
        provider.setId("fiscal-provider"); environment.setId("dian-settings-environment");
        Button saveProvider=button("Guardar selección",false); saveProvider.setId("fiscal-provider-save");
        provider.disableProperty().bind(providerBusy.or(providerReady.not())); saveProvider.disableProperty().bind(providerBusy.or(providerReady.not()));
        saveProvider.setOnAction(e->{ if(loadedProvider==null||provider.getValue()==null) return; providerBusy.set(true);
            async(()->api.guardarProveedorFiscal(selected(provider),loadedProvider.version()),r->{loadedProvider=r;providerBusy.set(false);providerState.setText("Selección guardada. DIAN directa permanece sin envío real.");},error->{providerBusy.set(false);providerState.setText("No se confirmó la selección: "+error.getMessage()+" Vuelve a abrir la vista para recargar.");}); });
        VBox mode=card("Proveedor para nuevas ventas",row(group("Proveedor",provider,330),saveProvider),
                note("Dataico conserva el envío actual. DIAN directa permite guardar XML sin firma; no envía facturas ni imprime un comprobante fiscal."),providerState);
        for(String id:List.of("name","nit","dv","street","duties","software-name","software-id","test-set-id","provider-nit","provider-dv","prefix","resolution","range-from","range-to","preview-number"))
            fields.put(id,field("dian-"+id,""));
        fields.get("duties").setPromptText("Ej. R-99-PN; códigos separados por ;");
        fields.get("software-id").setPromptText("UUID entregado por DIAN"); fields.get("test-set-id").setPromptText("UUID de habilitación (opcional)");
        fields.get("preview-number").setPromptText("No reserva un consecutivo");
        cities.stream().map(c->new Choice(c.departmentCode(),c.department())).distinct().sorted(Comparator.comparing(Choice::label)).forEach(department.getItems()::add);
        department.setOnAction(e->{city.getItems().clear(); if(department.getValue()!=null) city.getItems().addAll(cities.stream().filter(c->c.departmentCode().equals(department.getValue().code())).toList());});
        city.disableProperty().bind(department.valueProperty().isNull());
        Button sameIssuer=button("Usar NIT del emisor",false); sameIssuer.setOnAction(e->{fields.get("provider-nit").setText(text("nit"));fields.get("provider-dv").setText(text("dv"));});
        VBox issuer=card("Emisor y domicilio fiscal",row(group("Nombre o razón social *",fields.get("name"),340),group("Naturaleza *",nature,210),group("NIT sin DV *",fields.get("nit"),190),group("DV *",fields.get("dv"),75)),
                row(group("Dirección *",fields.get("street"),340),group("Departamento *",department,240),group("Municipio *",city,250)),
                row(group("Responsabilidades fiscales *",fields.get("duties"),340),group("Régimen tributario *",tax,240)),note("País: Colombia. Indica las responsabilidades del RUT; el programa no las deduce del NIT."));
        VBox software=card("Software registrado en DIAN",row(group("Nombre del software *",fields.get("software-name"),260),group("Software ID *",fields.get("software-id"),360),group("TestSetId",fields.get("test-set-id"),360)),
                row(group("NIT del proveedor de software *",fields.get("provider-nit"),260),group("DV *",fields.get("provider-dv"),75),sameIssuer));
        VBox numbering=card("Resolución y numeración",row(group("Prefijo",fields.get("prefix"),150),group("Resolución *",fields.get("resolution"),250),group("Válida desde *",validFrom,200),group("Válida hasta *",validUntil,200)),
                row(group("Rango desde *",fields.get("range-from"),200),group("Rango hasta *",fields.get("range-to"),200),group("Número para previsualizar *",fields.get("preview-number"),260)),
                note("La previsualización no consume números de producción. Los borradores no acreditan emisión ni aceptación DIAN."));
        Button choose=button("Seleccionar certificado .p12 / .pfx",false); choose.setId("dian-certificate-choose");
        choose.setOnAction(e->{var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PKCS#12","*.p12","*.pfx"));
            var file=chooser.showOpenDialog(getScene().getWindow()); if(file!=null) {certificate=file.toPath();certificateLabel.setText(file.getName());}});
        VBox credentials=card("Credenciales protegidas",secretState,row(group("Nueva clave técnica",technicalKey,300),group("Nuevo Software PIN",pin,240)),
                row(choose,certificateLabel,group("Contraseña del nuevo certificado",password,290)),
                note("Vacío conserva cada secreto existente. Las claves y el certificado se guardan cifrados en el servidor; no se muestran al recargar."));
        save.setId("dian-settings-save"); reload.setId("dian-settings-reload"); status.setId("dian-settings-status");
        save.disableProperty().bind(busy.or(loadedReady.not()));
        reload.disableProperty().bind(busy); environment.disableProperty().bind(busy);
        issuer.disableProperty().bind(busy);software.disableProperty().bind(busy);numbering.disableProperty().bind(busy);credentials.disableProperty().bind(busy);
        reload.setOnAction(e->load()); save.setOnAction(e->save());
        getChildren().addAll(mode,card("Configuración DIAN por ambiente",row(group("Ambiente",environment,320),reload),status),issuer,software,numbering,credentials,row(save));
        environment.getSelectionModel().selectFirst();
        environment.setOnAction(e->load());load();
        async(api::consultarProveedorFiscal,r->{loadedProvider=r;providerReady.set(true);select(provider,r.provider());providerState.setText("Proveedor activo: "+r.provider()+". La selección se conserva para nuevas ventas.");},error->providerState.setText("No se pudo consultar el proveedor: "+error.getMessage()+" Vuelve a abrir esta vista para reintentar."));
    }
    private void load() {
        loaded=null;loadedReady.set(false);clear(); busy.set(true); status(status,"Cargando configuración del ambiente seleccionado…",false);
        async(()->api.consultarConfiguracionDian(selected(environment)),r->{loaded=r;loadedReady.set(true);apply(r);busy.set(false);status(status,"Configuración de "+r.environment()+" cargada. * Campos obligatorios.",false);},this::failure);
    }
    private void clear() { fields.values().forEach(TextField::clear);technicalKey.clear();pin.clear();password.clear();certificate=null;certificateLabel.setText("No se seleccionó un archivo nuevo.");
        nature.getSelectionModel().clearSelection();tax.getSelectionModel().clearSelection();department.getSelectionModel().clearSelection();city.getItems().clear();validFrom.setValue(null);validUntil.setValue(null);secretState.setText(""); }
    private void apply(PosApiClient.DianSettingsResponse response) {
        secretState.setText("Clave técnica: "+flag(response.technicalKeyConfigured())+" · PIN: "+flag(response.softwarePinConfigured())+" · Certificado: "+flag(response.certificateConfigured()));
        var c=response.configuration(); if(c==null)return; var p=c.issuer();
        set("name",p.name());set("nit",p.identification());set("dv",p.checkDigit());set("street",p.address().street());set("duties",p.taxResponsibilities());select(nature,c.issuerOrganization());select(tax,p.taxSchemeId());
        select(department,p.address().departmentCode());city.getItems().stream().filter(v->v.code().equals(p.address().cityCode())).findFirst().ifPresent(city::setValue);
        set("software-name",c.softwareName());set("software-id",c.softwareId());set("test-set-id",c.testSetId());set("provider-nit",c.providerNit());set("provider-dv",c.providerDv());
        var n=c.numbering();set("prefix",n.prefix());set("resolution",n.resolution());set("range-from",Long.toString(n.rangeFrom()));set("range-to",Long.toString(n.rangeUntil()));set("preview-number",Long.toString(c.previewNumber()));validFrom.setValue(n.validFrom());validUntil.setValue(n.validUntil());
    }
    private void save() {
        try {
            if(loaded==null||department.getValue()==null||city.getValue()==null||validFrom.getValue()==null||validUntil.getValue()==null) throw new IllegalArgumentException("Completa el domicilio y las fechas obligatorias.");
            var address=new PosApiClient.DianAddress(text("street"),city.getValue().code(),city.getValue().name(),selected(department),department.getValue().label(),"CO");
            var party=new PosApiClient.DianParty("31",text("nit"),text("dv"),text("name"),text("duties"),selected(tax),tax.getValue().label(),address);
            UUID.fromString(text("software-id")); if(!text("test-set-id").isEmpty())UUID.fromString(text("test-set-id"));
            var n=new PosApiClient.DianNumbering(text("prefix"),text("resolution"),validFrom.getValue(),validUntil.getValue(),Long.parseLong(text("range-from")),Long.parseLong(text("range-to")));
            var config=new PosApiClient.DianConfiguration(text("software-name"),text("software-id"),text("test-set-id").isBlank()?null:text("test-set-id"),text("provider-nit"),text("provider-dv"),party,selected(nature),n,Long.parseLong(text("preview-number")));
            String encoded=null; if(certificate!=null) { if(Files.size(certificate)>512_000)throw new IllegalArgumentException("El certificado no puede superar 500 KB.");encoded=Base64.getEncoder().encodeToString(Files.readAllBytes(certificate)); }
            var request=new PosApiClient.SaveDianSettings(loaded.version(),config,technicalKey.getText(),pin.getText(),encoded,password.getText());
            String target=selected(environment);technicalKey.clear();pin.clear();password.clear();busy.set(true);status(status,"Guardando configuración de "+target+"…",false);
            async(()->api.guardarConfiguracionDian(target,request),r->{loaded=r;certificate=null;certificateLabel.setText("No se seleccionó un archivo nuevo.");apply(r);busy.set(false);status(status,"Configuración de "+r.environment()+" guardada. El envío directo sigue deshabilitado.",false);},this::failure);
        } catch(Exception error) { status(status,error instanceof NumberFormatException?"Revisa el rango y el número de previsualización.":"Revisa los campos: "+error.getMessage(),true); }
    }
    private void failure(RuntimeException error) {busy.set(false);technicalKey.clear();pin.clear();password.clear();status(status,"No se pudo confirmar la operación: "+error.getMessage()+" Recarga antes de reintentar.",true);}
    private String text(String key) {return fields.get(key).getText().trim();}
    private void set(String key,String value) {fields.get(key).setText(value==null?"":value);}
    private static String flag(boolean value) {return value?"configurado":"pendiente";}
}
