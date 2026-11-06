package com.posdesktop.pos.fiscal;

import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Flujo de teclado para ajustes; no contiene calculos fiscales ni seleccion de proveedor. */
public final class AdjustmentsPane extends VBox {
    private final PosApiClient api;private final boolean mayEdit,admin;
    private final TextField search=field("adjustment-search","Número de factura o cliente");
    private final ListView<PosApiClient.AdjustmentCandidate> results=new ListView<>();
    private final ComboBox<Choice> operation=choices("RETURN","1 · Devolución","CANCELLATION","2 · Anulación","INCREASE","3 · Ajustar valor");
    private final TableView<AdjustmentEditorRow> lines=new TableView<>();
    private final TableView<PosApiClient.AdjustmentSummary> history=new TableView<>();
    private final TextArea observation=new TextArea("Devolución de artículos.");
    private final Label sourceLabel=note("Busca una factura para comenzar."),status=note("F2 Buscar · F3 Operación · F4 Artículos · F6 Revisar · F8 Guardar · F9 Enviar"),totals=note("Total pendiente de revisión.");
    private final Button review=button("Revisar · F6",false),save=button("Guardar · F8",true),configure=button("Numeración · F7",false);
    private final Button send=button("Enviar a Dataico · F9",true),consult=button("Consultar estado",false),discard=button("Descartar borrador",false),evidence=button("Ver documento · F10",false),pdf=button("Descargar PDF",false);
    private final SimpleBooleanProperty busy=new SimpleBooleanProperty(),ready=new SimpleBooleanProperty(),reviewed=new SimpleBooleanProperty();
    private final TableColumn<AdjustmentEditorRow,BigDecimal> quantity=new TableColumn<>("A devolver"),increase=new TableColumn<>("Incremento / unidad");
    private PosApiClient.AdjustmentSource source;private PosApiClient.AdjustmentRequest request;
    private boolean editError;

    public AdjustmentsPane(PosApiClient api,boolean mayEdit,boolean admin){
        super(14);this.api=api;this.mayEdit=mayEdit;this.admin=admin;setId("adjustments-pane");
        Button find=button("Buscar · Enter",false);find.setOnAction(e->search());find.disableProperty().bind(busy);search.setOnAction(e->search());
        search.disableProperty().bind(busy);results.disableProperty().bind(busy);history.disableProperty().bind(busy);
        search.textProperty().addListener((o,a,b)->{source=null;ready.set(false);lines.getItems().clear();invalidate();sourceLabel.setText("Busca y selecciona la factura que deseas ajustar.");});
        results.setId("adjustment-results");results.setPrefHeight(105);results.setVisible(false);results.setManaged(false);
        results.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ENTER){openSelected();e.consume();}else if(e.getCode()==KeyCode.ESCAPE){showResults(false);search.requestFocus();e.consume();}});results.setOnMouseClicked(e->{if(e.getClickCount()==2)openSelected();});
        operation.setId("adjustment-kind");operation.setValue(operation.getItems().getFirst());operation.setOnAction(e->changeKind());
        operation.addEventFilter(KeyEvent.KEY_PRESSED,e->{int index=switch(e.getCode()){case DIGIT1,NUMPAD1->0;case DIGIT2,NUMPAD2->1;case DIGIT3,NUMPAD3->2;default->-1;};if(index>=0){operation.getSelectionModel().select(index);e.consume();}else if(e.getCode()==KeyCode.ENTER){focusLines();e.consume();}});
        operation.disableProperty().bind(busy.or(ready.not()));configure.setId("adjustment-numbering");configure.setVisible(admin);configure.setManaged(admin);configure.disableProperty().bind(busy.or(ready.not()));configure.setOnAction(e->configure());
        setupLines();observation.setId("adjustment-observation");observation.setPrefRowCount(2);observation.setWrapText(true);observation.getStyleClass().add("soft-area");observation.textProperty().addListener((o,a,b)->invalidate());observation.disableProperty().bind(busy.or(ready.not()));
        observation.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ENTER&&!e.isShiftDown()){review();e.consume();}});
        review.setId("adjustment-review");save.setId("adjustment-save");totals.setId("adjustment-totals");status.setId("adjustment-status");
        totals.getStyleClass().remove("card-subtitle");totals.getStyleClass().add("card-title");
        review.disableProperty().bind(busy.or(ready.not()).or(new SimpleBooleanProperty(!mayEdit)));save.disableProperty().bind(busy.or(reviewed.not()).or(new SimpleBooleanProperty(!mayEdit)));
        review.setOnAction(e->review());save.setOnAction(e->save());
        for(Button b:List.of(find,review,save,configure,send,consult,discard,evidence,pdf))b.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ENTER){b.fire();e.consume();}});
        sourceLabel.setId("adjustment-source");
        var editor=card("Factura original",row(group("Buscar factura · F2",search,440),find),results,sourceLabel,new Separator(),
                row(group("Operación · F3",operation,285),configure),note("Flechas: mover · Enter: editar y confirmar · + / −: una unidad · *: cantidades disponibles"),lines,
                group("Motivo / observación",observation,650),totals,row(review,save),status);
        getChildren().addAll(editor,historyPane());
        setOnKeyPressed(e->{Button button=switch(e.getCode()){case F6->review;case F8->save;case F9->send;case F10->evidence;default->null;};
            if(e.getCode()==KeyCode.F2){search.requestFocus();search.selectAll();e.consume();}else if(e.getCode()==KeyCode.F3){operation.requestFocus();e.consume();}else if(e.getCode()==KeyCode.F4){focusLines();e.consume();}else if(e.getCode()==KeyCode.F5){history.requestFocus();if(!history.getItems().isEmpty()&&history.getSelectionModel().isEmpty())history.getSelectionModel().selectFirst();e.consume();}else if(e.getCode()==KeyCode.F7&&admin){configure.fire();e.consume();}else if(button!=null){button.fire();e.consume();}});
        reloadHistory(null);Platform.runLater(search::requestFocus);
    }
    private void setupLines(){
        lines.setId("adjustment-lines");lines.getStyleClass().addAll("data-table","electronic-invoices-table");lines.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);lines.setEditable(true);lines.setPrefHeight(220);lines.setMinHeight(180);
        lines.setPlaceholder(note("Aquí aparecerán los artículos de la factura seleccionada."));lines.disableProperty().bind(busy.or(ready.not()));
        lines.getColumns().add(textColumn("Artículo",r->r.source.description(),230));lines.getColumns().add(textColumn("Original",r->r.source.originalQuantity().stripTrailingZeros().toPlainString(),80));
        lines.getColumns().add(textColumn("Disponible",r->r.source.available().stripTrailingZeros().toPlainString(),85));lines.getColumns().add(textColumn("Valor unitario",r->money(r.source.unitPrice()),110));lines.getColumns().add(textColumn("Impuestos",r->r.source.taxes(),110));
        quantity.setCellValueFactory(d->d.getValue().quantity);quantity.setPrefWidth(100);quantity.setCellFactory(c->new AdjustmentDecimalCell(this::cellError,()->{if("INCREASE".equals(kind())){int index=lines.getSelectionModel().getSelectedIndex();Platform.runLater(()->lines.edit(index,increase));}else nextRow();}));quantity.setOnEditCommit(e->{editError=false;e.getRowValue().quantity.set(e.getNewValue());invalidate();});
        increase.setCellValueFactory(d->d.getValue().increase);increase.setPrefWidth(140);increase.setCellFactory(c->new AdjustmentDecimalCell(this::cellError,this::nextRow));increase.setOnEditCommit(e->{editError=false;e.getRowValue().increase.set(e.getNewValue());invalidate();});increase.setVisible(false);lines.getColumns().addAll(quantity,increase);
        lines.getSelectionModel().setCellSelectionEnabled(true);
        lines.addEventFilter(KeyEvent.KEY_PRESSED,e->{if(!mayEdit||lines.getEditingCell()!=null)return;int index=lines.getSelectionModel().getSelectedIndex();if(index<0)return;
            var row=lines.getItems().get(index);boolean cancellation="CANCELLATION".equals(kind());
            if(e.getCode()==KeyCode.ENTER&&!cancellation){var focused=lines.getFocusModel().getFocusedCell().getTableColumn();lines.edit(index,focused==quantity?quantity:"INCREASE".equals(kind())?increase:quantity);e.consume();}
            else if(!cancellation&&(e.getCode()==KeyCode.ADD||e.getCode()==KeyCode.SUBTRACT)){BigDecimal value=row.quantity.get().add(e.getCode()==KeyCode.ADD?BigDecimal.ONE:BigDecimal.ONE.negate());BigDecimal max="INCREASE".equals(kind())?row.source.originalQuantity():row.source.available();row.quantity.set(value.max(BigDecimal.ZERO).min(max));invalidate();e.consume();}
            else if(!cancellation&&e.getCode()==KeyCode.MULTIPLY){for(var r:lines.getItems())r.quantity.set("INCREASE".equals(kind())?r.source.originalQuantity():r.source.available());invalidate();e.consume();}});
    }
    private VBox historyPane(){
        history.setId("adjustment-history");history.getStyleClass().addAll("data-table","electronic-invoices-table");history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);history.setPrefHeight(230);history.setMinHeight(180);history.setPlaceholder(note("Todavía no hay devoluciones, anulaciones o ajustes guardados."));
        history.getColumns().add(summaryColumn("Documento",PosApiClient.AdjustmentSummary::number,120));history.getColumns().add(summaryColumn("Factura original",PosApiClient.AdjustmentSummary::sourceNumber,140));history.getColumns().add(summaryColumn("Operación",v->label(v.kind()),125));history.getColumns().add(summaryColumn("Proveedor",v->"DATAICO".equals(v.provider())?"Dataico":"DIAN prueba",100));history.getColumns().add(summaryColumn("Estado",v->state(v.state())+(v.dianStatus()==null?"":" · "+v.dianStatus()),190));history.getColumns().add(summaryColumn("Total",v->money(v.total()),100));
        var selected=history.getSelectionModel().selectedItemProperty();
        send.disableProperty().bind(Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected.get()==null||!"DATAICO".equals(selected.get().provider())||!Set.of("BORRADOR","ERROR_SEGURO").contains(selected.get().state()),busy,selected));
        consult.disableProperty().bind(Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected.get()==null||!Set.of("INCIERTO","EN_CURSO","RECIBIDO").contains(selected.get().state()),busy,selected));
        discard.disableProperty().bind(Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected.get()==null||!Set.of("BORRADOR","ERROR_SEGURO").contains(selected.get().state()),busy,selected));
        evidence.disableProperty().bind(busy.or(selected.isNull()));pdf.disableProperty().bind(Bindings.createBooleanBinding(()->busy.get()||selected.get()==null||selected.get().pdfUrl()==null||selected.get().pdfUrl().isBlank(),busy,selected));
        send.setId("adjustment-send");consult.setId("adjustment-consult");discard.setId("adjustment-discard");evidence.setId("adjustment-evidence");
        send.setOnAction(e->action("enviar"));consult.setOnAction(e->action("consultar"));discard.setOnAction(e->action("descartar"));evidence.setOnAction(e->evidence());pdf.setOnAction(e->pdf());
        Button refresh=button("Actualizar",false);refresh.disableProperty().bind(busy);refresh.setOnAction(e->reloadHistory(null));
        return card("Historial · últimos 100 ajustes · F5",history,row(evidence,send,consult,discard,pdf,refresh),note("El envío fiscal y el reintegro de dinero son operaciones separadas. Los borradores no cambian la caja."));
    }
    private void search(){if(busy.get())return;if(search.getText().isBlank()){error("Escribe un número de factura o cliente.");search.requestFocus();return;}busy.set(true);message("Buscando facturas…");
        async(()->api.buscarFacturasAjuste(search.getText().trim()),found->{busy.set(false);results.getItems().setAll(found);showResults(!found.isEmpty());if(found.isEmpty()){message("No se encontraron facturas. Revisa el número o escribe parte del nombre.");search.requestFocus();}else{results.getSelectionModel().selectFirst();if(found.size()==1)openSelected();else{results.requestFocus();message("Elige la factura con las flechas y pulsa Enter.");}}},this::failure);}
    private void openSelected(){var selected=results.getSelectionModel().getSelectedItem();if(selected==null||busy.get())return;source=null;ready.set(false);invalidate();busy.set(true);showResults(false);message("Consultando el detalle original…");
        async(()->api.consultarOrigenAjuste(selected.provider(),selected.id()),s->{busy.set(false);applySource(s);message("Factura cargada. Elige la operación y las cantidades.");operation.requestFocus();},this::failure);}
    private void applySource(PosApiClient.AdjustmentSource s){source=s;ready.set(true);lines.getItems().setAll(s.lines().stream().map(AdjustmentEditorRow::new).toList());sourceLabel.setText(s.number()+" · "+s.customer()+" · "+("DATAICO".equals(s.provider())?"Dataico":"DIAN directa, borrador de prueba")+" · "+s.environment()+" · Total "+money(s.total()));changeKind();}
    private void changeKind(){if(operation.getValue()==null)return;editError=false;boolean cancel="CANCELLATION".equals(kind()),debit="INCREASE".equals(kind());quantity.setText(debit?"Unidades a ajustar":"A devolver");quantity.setEditable(!cancel);increase.setVisible(debit);lines.setEditable(!cancel&&mayEdit);
        for(var row:lines.getItems()){row.quantity.set(cancel?row.source.originalQuantity():BigDecimal.ZERO);row.increase.set(BigDecimal.ZERO);}
        observation.setText(switch(kind()){case "CANCELLATION"->"Anulación de la factura original.";case "INCREASE"->"Corrección por incremento del valor facturado.";default->"Devolución de artículos.";});invalidate();}
    private String kind(){return selected(operation);}
    private void review(){if(!mayEdit||source==null||busy.get())return;if(lines.getEditingCell()!=null||editError){error("Confirma la cantidad o el incremento con Enter antes de revisar.");return;}PosApiClient.AdjustmentRequest r=new PosApiClient.AdjustmentRequest(UUID.randomUUID().toString(),source.provider(),source.id(),source.sourceHash(),kind(),observation.getText(),lines.getItems().stream().map(AdjustmentEditorRow::selection).toList());busy.set(true);reviewed.set(false);message("Calculando impuestos y validando cantidades…");
        async(()->api.revisarAjuste(r),q->{busy.set(false);request=r;reviewed.set(true);totals.setText("Documento "+q.number()+" · Base "+money(q.plan().net())+" · Impuestos "+money(q.plan().tax())+" · Total "+money(q.plan().total()));message("Revisado. Pulsa F8 para guardar sin enviar.");save.requestFocus();},this::failure);}
    private void save(){if(!mayEdit||request==null||!reviewed.get()||busy.get())return;Runnable persist=()->{if(busy.get())return;busy.set(true);message("Guardando ajuste…");var frozen=request;
        async(()->api.guardarAjuste(frozen),saved->{busy.set(false);invalidate();message(saved.number()+" guardado. "+("DATAICO".equals(saved.provider())?"Selecciona Enviar a Dataico para transmitirlo.":"Abre Ver documento para firmar el XML de prueba."));reloadHistory(saved.id());refreshSource();},this::failure);};
        if("CANCELLATION".equals(request.kind()))confirm("Guardar anulación",source.number()+" · "+source.customer()+"\n"+totals.getText()+"\nLa factura original se conserva. El documento todavía no se enviará.",persist);else persist.run();}
    private void refreshSource(){var old=source;if(old==null)return;busy.set(true);async(()->api.consultarOrigenAjuste(old.provider(),old.id()),s->{busy.set(false);applySource(s);if(!send.isDisabled())send.requestFocus();else if(!evidence.isDisabled())evidence.requestFocus();},this::failure);}
    private void reloadHistory(String id){async(api::listarAjustes,list->{history.getItems().setAll(list);if(id!=null)list.stream().filter(v->id.equals(v.id())).findFirst().ifPresent(v->history.getSelectionModel().select(v));},e->error("No se pudo actualizar el historial: "+e.getMessage()));}
    private void action(String action){var s=history.getSelectionModel().getSelectedItem();if(s==null||busy.get())return;Runnable run=()->{busy.set(true);message("consultar".equals(action)?"Consultando el intento existente…":"Procesando "+s.number()+"…");async(()->api.accionAjuste(s.id(),action),r->{busy.set(false);message(r.number()+" · "+state(r.state())+(r.message()==null?"":". "+r.message()));reloadHistory(r.id());if("descartar".equals(action))refreshSource();},this::failure);};
        if("consultar".equals(action))run.run();else confirm("enviar".equals(action)?"Enviar a Dataico":"Descartar borrador",s.number()+" · "+label(s.kind())+"\nFactura original: "+s.sourceNumber()+"\nTotal: "+money(s.total())+"\n"+("enviar".equals(action)?"Se solicitará su envío a DIAN mediante Dataico.":"Se liberarán las cantidades. El número y la evidencia se conservarán."),run);}
    private void evidence(){var s=history.getSelectionModel().getSelectedItem();if(s==null)return;busy.set(true);async(()->api.consultarDocumentoFiscal(s.documentId()),d->{busy.set(false);FiscalDocumentsPane.showEvidence(getScene().getWindow(),api,d,mayEdit,()->reloadHistory(s.id()));},this::failure);}
    private void pdf(){var s=history.getSelectionModel().getSelectedItem();if(s==null||s.pdfUrl()==null)return;var chooser=new FileChooser();chooser.setInitialFileName(s.number()+".pdf");var file=chooser.showSaveDialog(getScene().getWindow());if(file==null)return;busy.set(true);async(()->{try{return new DataicoPdfDownloader().descargar(s.pdfUrl(),file.toPath());}catch(Exception e){throw new IllegalArgumentException(e.getMessage());}},p->{busy.set(false);message("PDF guardado.");},this::failure);}
    private void configure(){if(source==null||busy.get()||!admin)return;var current=source;var n=current.numbering();
        TextField cp=field("adjustment-credit-prefix","NC"),cn=field("adjustment-credit-next","1"),dp=field("adjustment-debit-prefix","ND"),dn=field("adjustment-debit-next","1");cp.setText(n.creditPrefix());cn.setText(Long.toString(n.creditNext()));dp.setText(n.debitPrefix());dn.setText(Long.toString(n.debitNext()));
        Label feedback=note("Usa la numeración de tu cuenta Dataico; para pruebas DIAN puedes usar NC y ND.");Button persist=button("Guardar numeración · F8",true),close=button("Cancelar · Esc",false);
        var body=new VBox(14,note(current.provider()+" · "+current.environment()+" · "+current.number()),row(group("Prefijo de devolución / anulación",cp,260),group("Siguiente número",cn,180)),row(group("Prefijo de incremento de valor",dp,260),group("Siguiente número",dn,180)),feedback,row(persist,close));body.setPadding(new javafx.geometry.Insets(18));
        var stage=new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY);stage.initOwner(getScene().getWindow());stage.initModality(javafx.stage.Modality.WINDOW_MODAL);stage.setTitle("Numeración de ajustes");var scene=new javafx.scene.Scene(body,560,380);scene.getStylesheets().addAll(getScene().getStylesheets());stage.setScene(scene);
        persist.setOnAction(e->{try{var r=new PosApiClient.AdjustmentConfigure(n.version(),cp.getText().trim().toUpperCase(Locale.ROOT),Long.parseLong(cn.getText().trim()),dp.getText().trim().toUpperCase(Locale.ROOT),Long.parseLong(dn.getText().trim()));persist.setDisable(true);close.setDisable(true);
            async(()->api.guardarNumeracionAjuste(current.provider(),current.id(),r),saved->{stage.close();refreshSource();message("Numeración guardada.");},error->{persist.setDisable(false);close.setDisable(false);status(feedback,error.getMessage(),true);});}catch(NumberFormatException error){status(feedback,"Los consecutivos deben ser números enteros positivos.",true);}});
        close.setOnAction(e->stage.close());body.setOnKeyPressed(e->{if(e.getCode()==KeyCode.F8){persist.fire();e.consume();}else if(e.getCode()==KeyCode.ESCAPE&&!close.isDisabled()){stage.close();e.consume();}});stage.show();cp.requestFocus();}
    private void confirm(String title,String text,Runnable accepted){var yes=new ButtonType("Confirmar",ButtonBar.ButtonData.OK_DONE);var no=new ButtonType("Cancelar",ButtonBar.ButtonData.CANCEL_CLOSE);var dialog=new Dialog<ButtonType>();dialog.initOwner(getScene().getWindow());dialog.setTitle(title);dialog.setHeaderText(title);dialog.setContentText(text);dialog.getDialogPane().getButtonTypes().addAll(yes,no);dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        Button y=(Button)dialog.getDialogPane().lookupButton(yes),n=(Button)dialog.getDialogPane().lookupButton(no);y.setDefaultButton(false);n.setDefaultButton(true);
        var accepts=new SimpleBooleanProperty();y.focusedProperty().addListener((o,a,b)->{if(b)accepts.set(true);});n.focusedProperty().addListener((o,a,b)->{if(b)accepts.set(false);});
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.LEFT||e.getCode()==KeyCode.RIGHT){accepts.set(!accepts.get());if(accepts.get())y.requestFocus();else n.requestFocus();e.consume();}else if(e.getCode()==KeyCode.ENTER){dialog.setResult(accepts.get()?yes:no);e.consume();}});
        var handled=new java.util.concurrent.atomic.AtomicBoolean();dialog.setOnShown(e->n.requestFocus());dialog.setOnHidden(e->{if(dialog.getResult()==yes&&handled.compareAndSet(false,true))accepted.run();});dialog.show();}
    private void focusLines(){if(!lines.getItems().isEmpty()){lines.requestFocus();lines.getSelectionModel().selectFirst();}}
    private void nextRow(){int next=lines.getSelectionModel().getSelectedIndex()+1;Platform.runLater(()->{if(next<lines.getItems().size()){lines.getSelectionModel().select(next);lines.requestFocus();}else observation.requestFocus();});}
    private void invalidate(){reviewed.set(false);request=null;totals.setText("Total pendiente de revisión · F6.");}
    private void showResults(boolean show){results.setVisible(show);results.setManaged(show);}
    private void message(String text){status(status,text,false);}
    private void error(String text){status(status,text,true);}
    private void cellError(String text){editError=true;error(text);}
    private void failure(RuntimeException error){busy.set(false);error(error.getMessage()==null?"No se pudo completar. Revisa la conexión e inténtalo nuevamente.":error.getMessage());}
    private TableColumn<AdjustmentEditorRow,String> textColumn(String name,Function<AdjustmentEditorRow,String> getter,double width){var c=new TableColumn<AdjustmentEditorRow,String>(name);c.setPrefWidth(width);c.setCellValueFactory(d->new ReadOnlyStringWrapper(getter.apply(d.getValue())));return c;}
    private TableColumn<PosApiClient.AdjustmentSummary,String> summaryColumn(String name,Function<PosApiClient.AdjustmentSummary,String> getter,double width){var c=new TableColumn<PosApiClient.AdjustmentSummary,String>(name);c.setPrefWidth(width);c.setCellValueFactory(d->new ReadOnlyStringWrapper(getter.apply(d.getValue())));return c;}
    private static String money(BigDecimal v){return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(v==null?BigDecimal.ZERO:v);}
    private static String label(String kind){return switch(kind){case "CANCELLATION"->"Anulación";case "INCREASE"->"Ajustar valor";default->"Devolución";};}
    private static String state(String state){return switch(state){case "BORRADOR"->"Guardado sin enviar";case "RECIBIDO"->"Recibido por Dataico";case "INCIERTO"->"Por confirmar";case "EN_CURSO"->"En curso";case "ERROR_SEGURO"->"Solicitud rechazada";case "DESCARTADO"->"Descartado";default->state;};}
}
