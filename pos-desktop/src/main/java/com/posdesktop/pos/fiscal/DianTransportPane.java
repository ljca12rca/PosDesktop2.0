package com.posdesktop.pos.fiscal;

import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.PosApiClient;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.util.List;

/** Acciones explicitas sobre un paquete inmutable; nunca envia al abrir la vista. */
public final class DianTransportPane extends VBox {
    private final PosApiClient api;private final String documentId;private final Runnable changed;
    private final BooleanProperty busy=new SimpleBooleanProperty();
    private final ComboBox<PosApiClient.DianSubmission> attempts=new ComboBox<>();
    private final Label mode=note("Cargando modo de transporte…"),status=note("Cargando paquetes…"),tracking=note("");
    private final TextArea evidence=new TextArea();
    private final Button prepare=button("Preparar ZIP · F6",false),send=button("Enviar · F9",true),query=button("Consultar · F10",false),export=button("Guardar ZIP",false),reload=button("Actualizar",false),cancel=button("Descartar sin enviar",false);
    public DianTransportPane(PosApiClient api,String documentId,boolean mayEdit,Runnable changed){
        super(14);this.api=api;this.documentId=documentId;this.changed=changed;setId("dian-transport-pane");
        attempts.setId("dian-transport-attempts");status.setId("dian-transport-status");send.setId("dian-transport-send");query.setId("dian-transport-query");prepare.setId("dian-transport-prepare");
        attempts.setConverter(new javafx.util.StringConverter<>(){public String toString(PosApiClient.DianSubmission s){return s==null?"":s.mode()+" · "+state(s.state())+" · "+s.zipName();}public PosApiClient.DianSubmission fromString(String s){return null;}});
        attempts.setMaxWidth(Double.MAX_VALUE);attempts.disableProperty().bind(busy);
        prepare.disableProperty().bind(busy.or(new SimpleBooleanProperty(!mayEdit)));reload.disableProperty().bind(busy);
        send.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected()==null||!selected().canSend(),busy,attempts.valueProperty()));
        query.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected()==null||!selected().canQuery(),busy,attempts.valueProperty()));
        export.disableProperty().bind(busy.or(attempts.valueProperty().isNull()));
        cancel.setId("dian-transport-cancel");cancel.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->busy.get()||!mayEdit||selected()==null||!selected().canCancel(),busy,attempts.valueProperty()));
        evidence.setEditable(false);evidence.setWrapText(true);evidence.setPrefRowCount(11);evidence.setId("dian-transport-evidence");evidence.getStyleClass().add("soft-area");
        evidence.setPromptText("Aquí aparecerán los errores, el ApplicationResponse y las respuestas del seguimiento.");evidence.setAccessibleText("Evidencia de respuestas DIAN, solo lectura");
        getChildren().addAll(card("Transporte DIAN · pruebas",mode,note("Recibir un ZIP no equivale a aceptar la factura. Cada envío conserva su seguimiento y sus respuestas; no se repite automáticamente."),row(prepare,reload),group("Paquetes de este documento",attempts,790),row(send,query,export,cancel),status,tracking,evidence));
        prepare.setOnAction(e->{work("Preparando paquete…");async(()->api.prepararEnvioDian(documentId),s->{busy.set(false);changed.run();load(s.id());},this::failure);});
        send.setOnAction(e->execute(false));query.setOnAction(e->execute(true));reload.setOnAction(e->load(selected()==null?null:selected().id()));
        attempts.setOnAction(e->{if(selected()!=null)detail(selected().id());});export.setOnAction(e->export());
        cancel.setOnAction(e->{var s=selected();if(s==null)return;work("Descartando paquete sin enviar…");async(()->api.descartarPaqueteDian(s.id()),r->{busy.set(false);changed.run();load(r.id());},this::failure);});
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{Button action=switch(e.getCode()){case F6->prepare;case F9->send;case F10->query;default->null;};if(action!=null){if(!action.isDisabled())action.fire();e.consume();}});
        load(null);
    }
    private PosApiClient.DianSubmission selected(){return attempts.getValue();}
    private record Loaded(PosApiClient.DianTransportSettings settings,List<PosApiClient.DianSubmission> submissions){}
    private void load(String preferred){work("Cargando paquetes…");async(()->new Loaded(api.consultarTransporteDian(),api.listarEnviosDian(documentId)),r->{busy.set(false);mode.setText("Modo para nuevos paquetes: "+r.settings().mode()+". "+r.settings().explanation());attempts.getItems().setAll(r.submissions());if(r.submissions().isEmpty()){status(status,"Sin paquetes. Prepara el ZIP de la copia firmada para continuar.",false);tracking.setText("");evidence.clear();}else{attempts.setValue(r.submissions().stream().filter(s->s.id().equals(preferred)).findFirst().orElse(r.submissions().getFirst()));}},this::failure);}
    private void detail(String id){work("Cargando respuesta…");async(()->api.consultarEnvioDian(id),r->{busy.set(false);var s=r.summary();send.setText("SIMULACION".equals(s.mode())?"Simular envío · F9":"Enviar a habilitación · F9");status(status,state(s.state())+". "+s.message(),s.state().endsWith("RECHAZADO")||s.state().endsWith("INCIERTO"));tracking.setText("Seguimiento: "+(s.trackId()==null?"aún no recibido":s.trackId()));StringBuilder text=new StringBuilder();if(r.result()!=null){text.append("Código: ").append(r.result().code()==null?"—":r.result().code()).append('\n');r.result().errors().forEach(error->text.append("• ").append(error).append('\n'));if(r.result().applicationResponse()!=null)text.append("\nApplicationResponse:\n").append(r.result().applicationResponse()).append('\n');}r.exchanges().forEach(x->text.append("\n").append(x.createdAt()).append(" · ").append(x.action()).append(" · HTTP ").append(x.httpStatus()==null?"sin respuesta":x.httpStatus()).append("\n").append(x.diagnosis()).append("\n").append(x.responseXml()==null?"":x.responseXml()).append('\n'));evidence.setText(text.toString());},this::failure);}
    private void execute(boolean query){var s=selected();if(s==null)return;work(query?"Consultando resultado…":"Enviando paquete…");async(()->api.accionEnvioDian(s.id(),query),r->{busy.set(false);changed.run();load(r.id());},this::failure);}
    private void export(){var s=selected();if(s==null)return;var chooser=new FileChooser();chooser.setInitialFileName(s.zipName());chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Paquete DIAN","*.zip"));var file=chooser.showSaveDialog(getScene().getWindow());if(file==null)return;work("Guardando ZIP…");async(()->{byte[] bytes=api.descargarZipDian(s.id());try{Files.write(file.toPath(),bytes);return true;}catch(java.io.IOException e){throw new IllegalStateException("No se pudo guardar el archivo.");}},r->{busy.set(false);status(status,"ZIP guardado.",false);},this::failure);}
    private void work(String message){busy.set(true);status(status,message,false);}
    private void failure(RuntimeException error){busy.set(false);status(status,error.getMessage()+" Usa Actualizar para revisar el estado guardado.",true);}
    static String state(String value){if(value.startsWith("SIMULACION_"))return "Simulación local · "+value.substring(11).toLowerCase().replace('_',' ');return switch(value){case "DIAN_ACEPTADO"->"Aceptado por DIAN";case "DIAN_RECHAZADO"->"Rechazado por DIAN";case "DIAN_RECIBIDO"->"ZIP recibido · pendiente de validación";case "DIAN_INCIERTO"->"Resultado por confirmar";default->value.replace("DIAN_","").toLowerCase().replace('_',' ');};}
}
