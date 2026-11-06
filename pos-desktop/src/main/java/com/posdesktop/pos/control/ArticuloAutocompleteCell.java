package com.posdesktop.pos.control;

import com.posdesktop.pos.integration.PosApiClient.SugerenciaArticuloResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/** Editor libre con sugerencias opcionales; elegir un nombre nunca cambia el precio de la fila. */
public final class ArticuloAutocompleteCell<R> extends TableCell<R, String> {
    private final TextField editor = new TextField();
    private final ContextMenu popup = new ContextMenu();
    private final PauseTransition delay = new PauseTransition(Duration.millis(220));
    private final Function<R, BigDecimal> precioFila;
    private final BiFunction<String, BigDecimal, List<SugerenciaArticuloResponse>> buscar;
    private final Runnable siguienteCampo;
    private int version;
    private int seleccion = -1;
    private List<SugerenciaArticuloResponse> resultados = List.of();

    public ArticuloAutocompleteCell(Function<R, BigDecimal> precioFila,
            BiFunction<String, BigDecimal, List<SugerenciaArticuloResponse>> buscar,
            Runnable siguienteCampo) {
        this.precioFila = precioFila;
        this.buscar = buscar;
        this.siguienteCampo = siguienteCampo;
        editor.getStyleClass().add("soft-field");
        editor.setAccessibleText("Nombre del artículo. Flechas para sugerencias, Enter para confirmar, Escape para cancelar.");
        editor.setPromptText("Escribe o elige un artículo");
        editor.setTextFormatter(new TextFormatter<String>(change -> {
            String texto = change.getText();
            String mayusculas = texto.toUpperCase(Locale.ROOT);
            int finInsertado = change.getRangeStart() + texto.length();
            int diferencia = mayusculas.length() - texto.length();
            int ancla = change.getAnchor();
            int cursor = change.getCaretPosition();
            change.setText(mayusculas);
            // Algunas letras se expanden al convertirlas (ß → SS): conservar la posición de edición.
            change.selectRange(ancla >= finInsertado ? ancla + diferencia : ancla,
                    cursor >= finInsertado ? cursor + diferencia : cursor);
            return change.getControlNewText().length() <= 200 ? change : null;
        }));
        popup.getStyleClass().add("customer-autocomplete-menu");
        editor.textProperty().addListener((obs, anterior, texto) -> programarBusqueda());
        editor.addEventFilter(KeyEvent.KEY_PRESSED, this::tecla);
        popup.addEventFilter(KeyEvent.KEY_PRESSED, this::tecla);
        editor.focusedProperty().addListener((obs, anterior, enfocado) -> {
            // ContextMenu conserva el foco del editor. Confirmar antes del clic en Cobrar u otro campo.
            if (!enfocado && isEditing()) confirmar(false);
        });
        setTooltip(new Tooltip("Doble clic para escribir un nombre o buscar artículos por precio y texto."));
    }

    @Override
    public void startEdit() {
        if (isEmpty() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
        super.startEdit();
        String actual = getItem();
        editor.setText(esGenerico(actual) ? "" : actual);
        setText(null);
        setGraphic(editor);
        Platform.runLater(() -> {
            if (!isEditing()) return;
            editor.requestFocus();
            editor.selectAll();
            programarBusqueda();
        });
    }

    private boolean esGenerico(String nombre) {
        return nombre == null || nombre.equalsIgnoreCase("Artículo manual")
                || nombre.equalsIgnoreCase("Articulo manual") || nombre.equalsIgnoreCase("Venta manual");
    }

    private void programarBusqueda() {
        int solicitud = ++version;
        delay.stop();
        popup.hide();
        seleccion = -1;
        resultados = List.of();
        if (!isEditing()) return;
        String texto = editor.getText().trim();
        BigDecimal precio = precioFila.apply(getTableRow().getItem());
        delay.setOnFinished(event -> {
            mostrarMensaje("Buscando artículos…");
            CompletableFuture.supplyAsync(() -> buscar.apply(texto, precio)).whenComplete((items, error) ->
                    Platform.runLater(() -> {
                        if (solicitud != version || !isEditing() || !editor.isFocused()) return;
                        if (error != null) {
                            mostrarMensaje("Sugerencias no disponibles. Puedes escribir el nombre.");
                        } else {
                            mostrarResultados(items);
                        }
                    }));
        });
        delay.playFromStart();
    }

    private void mostrarResultados(List<SugerenciaArticuloResponse> items) {
        resultados = items == null ? List.of() : items;
        seleccion = -1; // Enter conserva el texto manual hasta que el usuario elija con flechas o clic.
        popup.getItems().clear();
        if (resultados.isEmpty()) {
            mostrarMensaje(editor.getText().isBlank() ? "Sin artículos para este precio. Escribe un nombre."
                    : "Sin coincidencias. El nombre nuevo se guardará al cobrar.");
            return;
        }
        for (int i = 0; i < resultados.size(); i++) {
            SugerenciaArticuloResponse articulo = resultados.get(i);
            int indice = i;
            Label label = opcion(articulo.nombre());
            label.setTooltip(new Tooltip(label.getText()));
            CustomMenuItem item = new CustomMenuItem(label, true);
            label.setOnMouseEntered(event -> seleccionar(indice));
            item.setOnAction(event -> elegir(indice));
            popup.getItems().add(item);
        }
        mostrarPopup();
    }

    private Label opcion(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("customer-autocomplete-option");
        label.setMaxWidth(500);
        return label;
    }

    private void mostrarMensaje(String texto) {
        resultados = List.of();
        seleccion = -1;
        CustomMenuItem item = new CustomMenuItem(opcion(texto), false);
        item.setDisable(true);
        popup.getItems().setAll(item);
        mostrarPopup();
    }

    private void mostrarPopup() {
        if (isEditing() && editor.isFocused() && editor.getScene() != null
                && editor.getScene().getWindow() != null && editor.getScene().getWindow().isShowing()) {
            popup.show(editor, Side.BOTTOM, 0, 2);
        }
    }

    private void seleccionar(int indice) {
        seleccion = indice;
        for (int i = 0; i < popup.getItems().size(); i++) {
            var styles = popup.getItems().get(i).getStyleClass();
            styles.remove("customer-autocomplete-selected");
            if (i == seleccion) styles.add("customer-autocomplete-selected");
        }
    }

    private void tecla(KeyEvent event) {
        if (!isEditing()) return;
        if (event.getCode() == KeyCode.DOWN && !resultados.isEmpty()) {
            seleccionar(Math.min(seleccion + 1, resultados.size() - 1));
        } else if (event.getCode() == KeyCode.UP && !resultados.isEmpty()) {
            seleccionar(Math.max(seleccion - 1, 0));
        } else if (event.getCode() == KeyCode.ENTER) {
            if (popup.isShowing() && seleccion >= 0) elegir(seleccion);
            else confirmar(true);
        } else if (event.getCode() == KeyCode.TAB) {
            confirmar(false);
            return; // Mantener la navegación de foco habitual.
        } else if (event.getCode() == KeyCode.ESCAPE) {
            cancelEdit();
        } else return;
        event.consume();
    }

    private void elegir(int indice) {
        String nombre = resultados.get(indice).nombre();
        cerrar();
        editor.setText(nombre);
        confirmar(true);
    }

    private void confirmar(boolean avanzar) {
        if (!isEditing()) return;
        String nombre = editor.getText().trim();
        commitEdit(nombre.isBlank() ? getItem() : nombre);
        if (avanzar) Platform.runLater(siguienteCampo);
    }

    private void cerrar() {
        ++version;
        delay.stop();
        popup.hide();
        resultados = List.of();
        seleccion = -1;
    }

    @Override
    public void commitEdit(String nombre) {
        cerrar();
        super.commitEdit(nombre);
        setGraphic(null);
        setText(nombre);
    }

    @Override
    public void cancelEdit() {
        cerrar();
        super.cancelEdit();
        setGraphic(null);
        setText(getItem());
    }

    @Override
    protected void updateItem(String nombre, boolean empty) {
        super.updateItem(nombre, empty);
        if (empty) {
            cerrar();
            setGraphic(null);
            setText(null);
        } else if (isEditing()) {
            setText(null);
            setGraphic(editor);
        } else {
            setGraphic(null);
            setText(nombre);
        }
    }
}
