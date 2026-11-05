package com.posdesktop.pos.fiscal;

import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.PosApiClient;
import javafx.beans.property.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public final class FiscalDocumentsPane extends VBox {
    private final PosApiClient api;
    private final boolean mayEdit;
    private final TableView<PosApiClient.FiscalDocumentSummary> table=new TableView<>();
    private final Label status=note("Cargando documentos fiscales…");
    private final SimpleBooleanProperty busy=new SimpleBooleanProperty();
    public FiscalDocumentsPane(PosApiClient api,boolean mayEdit) {
        super(16);this.api=api;this.mayEdit=mayEdit;setId("fiscal-documents-pane");table.setId("fiscal-documents-table");
        table.getStyleClass().addAll("data-table","electronic-invoices-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPrefHeight(340);table.setMinHeight(230);
        table.setPlaceholder(note("Aún no hay documentos fiscales de esta versión. Las facturas históricas Dataico siguen en Facturas electrónicas."));
        table.getColumns().add(column("Fecha",v->v.createdAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),140));
        table.getColumns().add(column("Documento",PosApiClient.FiscalDocumentSummary::number,150));table.getColumns().add(column("Cliente",PosApiClient.FiscalDocumentSummary::customer,210));
        table.getColumns().add(column("Proveedor",v->"DATAICO".equals(v.provider())?"Dataico":"DIAN directa",120));table.getColumns().add(column("Ambiente",PosApiClient.FiscalDocumentSummary::environment,105));
        table.getColumns().add(column("Estado",v->displayState(v.state()),160));table.getColumns().add(column("Total",v->"$ "+v.total(),100));
        Button reload=button("Actualizar",false),view=button("Ver evidencia",true),consult=button("Consultar intento Dataico",false),retry=button("Reintentar mismo documento",false);
        reload.setId("fiscal-documents-reload");view.setId("fiscal-document-view");consult.setId("fiscal-attempt-consult");retry.setId("fiscal-attempt-retry");
        reload.disableProperty().bind(busy);view.disableProperty().bind(busy.or(table.getSelectionModel().selectedItemProperty().isNull()));
        consult.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->busy.get()||!mayEdit||!selectedAttempt(false),busy,table.getSelectionModel().selectedItemProperty()));
        retry.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->busy.get()||!mayEdit||!selectedAttempt(true),busy,table.getSelectionModel().selectedItemProperty()));
        reload.setOnAction(e->load());view.setOnAction(e->{var selected=table.getSelectionModel().getSelectedItem();if(selected==null)return;busy.set(true);
            async(()->api.consultarDocumentoFiscal(selected.id()),d->{busy.set(false);showEvidence(d);},this::failure);});
        consult.setOnAction(e->attempt(false));retry.setOnAction(e->attempt(true));
        TextField saleId=field("fiscal-sale-id","Ej. VTA-20261030-0001");Button draft=button("Preparar borrador DIAN",false);draft.disableProperty().bind(busy.or(new SimpleBooleanProperty(!mayEdit)));
        draft.setOnAction(e->{if(saleId.getText().isBlank()){status(status,"Ingresa el número del comprobante local.",true);return;}
            busy.set(true);async(()->api.consultarVentaFiscal(saleId.getText().trim()),s->{busy.set(false);showDraft(getScene().getWindow(),api,s,null,"",r->{status(status,"Borrador guardado sin firma ni envío.",false);load();});},this::failure);});
        status.setId("fiscal-documents-status");getChildren().addAll(card("Documentos y evidencia",note("Últimos 200 documentos. El estado recibido confirma el registro en Dataico; la aceptación DIAN se consulta en Facturas electrónicas."),row(reload,view,consult,retry),table,status),
                card("Borrador de una venta existente",row(group("Número del comprobante local",saleId,420),draft),note("Al finalizar una venta con DIAN directa se abrirá este formulario automáticamente. Aquí también puedes retomar una venta guardada.")));
        VBox.setVgrow(table,Priority.ALWAYS);load();
    }
    private boolean selectedAttempt(boolean retry){var s=table.getSelectionModel().getSelectedItem();return s!=null&&"DATAICO".equals(s.provider())&&"ENVIO".equals(s.purpose())
            &&(retry?"ERROR_SEGURO".equals(s.state()):java.util.Set.of("INCIERTO","EN_CURSO").contains(s.state()));}
    private static String displayState(String state){return switch(state){case "FIRMADO_LOCAL"->"Firmado local · sin envío";case "BORRADOR_SIN_FIRMA"->"Borrador sin firma";case "CONFIRMADO"->"Recibida por Dataico";case "EN_CURSO"->"En curso";case "INCIERTO"->"Por confirmar";case "ERROR_SEGURO"->"Envío rechazado";case "NUMERO_RECHAZADO"->"Número rechazado";default->"Sin confirmar";};}
    private void attempt(boolean retry){var s=table.getSelectionModel().getSelectedItem();if(s==null)return;busy.set(true);status(status,retry?"Reintentando el mismo documento…":"Consultando el intento existente…",false);
        async(()->retry?api.reintentarFacturaFiscal(s.saleId(),s.customerId()):api.consultarIntentoFiscal(s.saleId()),r->{busy.set(false);load();},this::failure);}
    private void load(){busy.set(true);status(status,"Cargando documentos…",false);async(api::listarDocumentosFiscales,list->{table.getItems().setAll(list);busy.set(false);status(status,list.size()+" documentos consultados (máximo 200).",false);},this::failure);}
    private void failure(RuntimeException error){busy.set(false);status(status,"No se pudo completar: "+error.getMessage(),true);}
    private TableColumn<PosApiClient.FiscalDocumentSummary,String> column(String title,Function<PosApiClient.FiscalDocumentSummary,String> value,double width){var c=new TableColumn<PosApiClient.FiscalDocumentSummary,String>(title);c.setCellValueFactory(d->new ReadOnlyStringWrapper(value.apply(d.getValue())));c.setPrefWidth(width);return c;}
    private void showEvidence(PosApiClient.FiscalDocumentDetail detail){
        TextArea text=new TextArea(detail.xml()==null?detail.snapshot():detail.xml());text.setEditable(false);text.setWrapText(false);text.getStyleClass().add("soft-area");text.setPrefRowCount(18);
        boolean signed="DIAN_SIGNED_LOCAL_V1".equals(detail.format());
        Label message=note(detail.xml()==null?"Snapshot de la solicitud Dataico. No contiene un XML DIAN local.":signed?"FIRMADO LOCALMENTE. Sin envío ni aceptación DIAN. Confianza del certificado y revocación pendientes.":"BORRADOR SIN FIRMA. No acredita emisión ni aceptación DIAN.");
        var working=new SimpleBooleanProperty();var completed=new SimpleBooleanProperty();
        Button sign=button("Firmar copia de prueba",true),verify=button("Verificar firma local",false);
        sign.setId("fiscal-sign-local");verify.setId("fiscal-verify-local");
        sign.setVisible(mayEdit && "DIAN_INVOICE_V1".equals(detail.format()) && "PRUEBAS".equals(detail.summary().environment()));sign.setManaged(sign.isVisible());
        verify.setVisible(signed);verify.setManaged(signed);sign.disableProperty().bind(working.or(completed));verify.disableProperty().bind(working);
        sign.setOnAction(e->{working.set(true);status(message,"Firmando y verificando copia local…",false);
            async(()->api.firmarDocumentoLocal(detail.summary().id()),r->{working.set(false);completed.set(true);status(message,"Copia firmada y validada localmente. Sin envío a DIAN. Ábrela desde la tabla para exportarla.",false);load();},error->{working.set(false);status(message,error.getMessage(),true);});});
        verify.setOnAction(e->{working.set(true);status(message,"Verificando integridad y firma…",false);
            async(()->api.validarFirmaLocal(detail.summary().id()),r->{working.set(false);status(message,r.valid()?"Firma e integridad verificadas localmente. Sin validación de confianza ni aceptación DIAN.":"La firma local no es válida. "+String.join(" ",r.issues()),!r.valid());},error->{working.set(false);status(message,error.getMessage(),true);});});
        Button export=button(detail.xml()==null?"Guardar snapshot JSON":"Guardar XML",false);export.setOnAction(e->{var chooser=new FileChooser();chooser.setInitialFileName(detail.summary().number()+(detail.xml()==null?".json":".xml"));
            var file=chooser.showSaveDialog(getScene().getWindow());if(file!=null)try{Files.writeString(file.toPath(),text.getText(),StandardCharsets.UTF_8);message.setText("Archivo guardado.");}catch(Exception error){message.setText("No se pudo guardar el archivo.");}});
        String history=String.join("\n",detail.events().stream().map(v->v.createdAt()+" · "+v.type()+": "+v.detail()).toList());
        VBox content=new VBox(12,message,note("Proveedor: "+detail.summary().provider()+" · Estado: "+displayState(detail.summary().state())),text,note("SHA-256: "+detail.hash()),note(history),row(sign,verify,export));content.setPadding(new javafx.geometry.Insets(18));
        showWindow(getScene().getWindow(),"Evidencia fiscal · "+detail.summary().number(),content,960,700);
    }
    public static void showDraft(Window owner,PosApiClient api,PosApiClient.VentaRegistradaResponse sale,String customerId,String customerName,java.util.function.Consumer<PosApiClient.DianDraftResult> saved){
        var content=new DianDraftPane(api,sale,customerId,customerName,saved);content.setPadding(new javafx.geometry.Insets(18));showWindow(owner,"Borrador DIAN · "+sale.numeroVenta(),content,1040,740);
    }
    private static void showWindow(Window owner,String title,VBox content,double width,double height){
        Stage stage=new Stage(StageStyle.UTILITY);stage.initOwner(owner);stage.initModality(Modality.WINDOW_MODAL);stage.setTitle(title);
        Button close=button("Cerrar",false);close.setOnAction(e->stage.close());content.getChildren().add(close);
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);content.setMinHeight(Region.USE_PREF_SIZE);
        var bounds=Screen.getPrimary().getVisualBounds();stage.setWidth(Math.min(width,bounds.getWidth()-40));stage.setHeight(Math.min(height,bounds.getHeight()-60));
        Scene scene=new Scene(scroll);scene.getStylesheets().add(java.util.Objects.requireNonNull(FiscalDocumentsPane.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css")).toExternalForm());stage.setScene(scene);stage.show();
    }
}
