package com.posdesktop.pos.fiscal;
import static com.posdesktop.pos.fiscal.FiscalUi.*;
import com.posdesktop.pos.integration.PosApiClient;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.stream.Collectors;

/** Configuracion ADMIN; las contrasenas nunca vuelven desde el servidor. */
public final class FiscalDeliverySettingsPane extends VBox {
 private final PosApiClient api;private PosApiClient.FiscalDeliverySettings loaded;
 private final BooleanProperty busy=new SimpleBooleanProperty(),ready=new SimpleBooleanProperty();
 private final ComboBox<Choice> mode=choices("SIMULACION","Simulacion local - sin correo","SMTP","SMTP - correos de prueba"),security=choices("STARTTLS","STARTTLS obligatorio","SSL","TLS / SSL","LOCAL","Sin TLS - solo localhost");
 private final TextField host=field("delivery-smtp-host","smtp.ejemplo.com"),port=field("delivery-smtp-port","587"),username=field("delivery-smtp-user","Usuario SMTP"),from=field("delivery-smtp-from","facturacion@empresa.com"),name=field("delivery-smtp-name","Nombre del remitente");
 private final PasswordField password=secret("delivery-smtp-password");private final TextArea fingerprints=new TextArea();private final Label status=note("Cargando configuracion de entrega…");
 public FiscalDeliverySettingsPane(PosApiClient api){super(12);this.api=api;setId("fiscal-delivery-settings");mode.setId("delivery-mail-mode");fingerprints.setId("delivery-response-fingerprints");fingerprints.setPromptText("Huellas SHA-256 de certificados publicos DIAN verificados por un administrador; una por linea. Vacio no confia en certificados embebidos.");fingerprints.setWrapText(true);fingerprints.setPrefRowCount(3);fingerprints.getStyleClass().add("soft-area");
  Button save=button("Guardar entrega y confianza",false),reload=button("Recargar entrega",false);save.setId("delivery-settings-save");save.disableProperty().bind(busy.or(ready.not()));reload.disableProperty().bind(busy);
  VBox controls=new VBox(12,row(group("Modo de correo",mode,300),group("Seguridad SMTP",security,250)),row(group("Servidor SMTP",host,300),group("Puerto",port,100),group("Usuario",username,270)),row(group("Correo remitente",from,300),group("Nombre remitente",name,240),group("Nueva contrasena SMTP",password,260)),group("Certificados DIAN confiables para respuestas (huellas SHA-256)",fingerprints,900));controls.disableProperty().bind(busy.or(ready.not()));
  getChildren().add(card("PDF, correo y confianza de respuestas",note("La simulacion no envia mensajes. SMTP envia correos de PRUEBA solo al ejecutar la accion desde el documento. No necesitas el certificado real del emisor para probar este flujo."),controls,row(save,reload),status));
  reload.setOnAction(e->load());save.setOnAction(e->save());load();
 }
 private void load(){busy.set(true);ready.set(false);password.clear();async(api::consultarEntregaFiscal,r->{loaded=r;var c=r.configuration();select(mode,c.mode());select(security,c.security());host.setText(c.host());port.setText(Integer.toString(c.port()));username.setText(c.username());from.setText(c.from());name.setText(c.senderName());fingerprints.setText(String.join("\n",new TreeSet<>(c.responseCertificateFingerprints())));busy.set(false);ready.set(true);status(status,"Configuracion cargada. Contrasena SMTP: "+(r.passwordConfigured()?"guardada; vacio conserva":"no configurada")+". No se confia automaticamente en certificados recibidos.",false);},this::failure);}
 private void save(){try{var c=new PosApiClient.FiscalDeliveryConfiguration(selected(mode),host.getText().trim(),Integer.parseInt(port.getText().trim()),selected(security),username.getText().trim(),from.getText().trim(),name.getText().trim(),Arrays.stream(fingerprints.getText().split("[\\s,;]+")).filter(s->!s.isBlank()).map(s->s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()));var request=new PosApiClient.FiscalDeliverySave(c,loaded.version(),password.getText());busy.set(true);async(()->api.guardarEntregaFiscal(request),r->{loaded=r;password.clear();busy.set(false);status(status,"Configuracion de entrega guardada. No se envio ningun correo.",false);},this::failure);}catch(NumberFormatException e){status(status,"El puerto SMTP debe ser numerico.",true);port.requestFocus();}}
 private void failure(RuntimeException e){busy.set(false);ready.set(false);password.clear();status(status,e.getMessage()+" Recarga para continuar.",true);}
}
