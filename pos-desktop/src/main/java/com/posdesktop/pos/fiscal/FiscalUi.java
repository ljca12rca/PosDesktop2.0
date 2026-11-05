package com.posdesktop.pos.fiscal;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

final class FiscalUi {
    private FiscalUi() { }
    record Choice(String code,String label) { @Override public String toString() { return label; } }
    record City(String departmentCode,String department,String code,String name) { @Override public String toString() { return name; } }
    static TextField field(String id,String prompt) {
        TextField field=new TextField(); field.setId(id); field.setPromptText(prompt); field.getStyleClass().add("soft-field"); return field;
    }
    static PasswordField secret(String id) {
        PasswordField field=new PasswordField(); field.setId(id); field.setPromptText("Vacío conserva el actual"); field.getStyleClass().add("soft-field"); return field;
    }
    static Label note(String text) { Label label=new Label(text); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE); label.getStyleClass().add("card-subtitle"); return label; }
    static VBox group(String label,Node input,double width) {
        Label title=new Label(label); title.getStyleClass().add("field-label"); title.setLabelFor(input);
        if (input instanceof Region region) { region.setMaxWidth(Double.MAX_VALUE); region.setPrefWidth(width); }
        VBox group=new VBox(5,title,input); group.setPrefWidth(width); group.setMinWidth(Math.min(width,150)); return group;
    }
    static FlowPane row(Node... nodes) { FlowPane row=new FlowPane(14,12,nodes); row.getStyleClass().add("fiscal-flow"); return row; }
    static VBox card(String title,Node... nodes) {
        Label heading=new Label(title); heading.getStyleClass().add("card-title");
        VBox card=new VBox(14); card.getStyleClass().add("surface-card"); card.getChildren().add(heading); card.getChildren().addAll(nodes);
        card.setMinHeight(Region.USE_PREF_SIZE); return card;
    }
    static Button button(String label,boolean primary) { Button b=new Button(label); b.getStyleClass().add(primary?"primary-button":"ghost-button"); b.setMinWidth(Region.USE_PREF_SIZE); return b; }
    static ComboBox<Choice> choices(String... pairs) {
        ComboBox<Choice> box=new ComboBox<>();
        for(int i=0;i<pairs.length;i+=2) box.getItems().add(new Choice(pairs[i],pairs[i+1]));
        return box;
    }
    static void select(ComboBox<Choice> box,String code) { box.getSelectionModel().clearSelection(); box.getItems().stream().filter(c->c.code().equals(code)).findFirst().ifPresent(box::setValue); }
    static String selected(ComboBox<Choice> box) { if(box.getValue()==null) throw new IllegalArgumentException("Completa las opciones obligatorias."); return box.getValue().code(); }
    static void status(Label label,String text,boolean error) { label.setText(text); label.getStyleClass().removeAll("status-error","status-success"); if(error) label.getStyleClass().add("status-error"); }
    static <T> void async(Supplier<T> work,Consumer<T> success,Consumer<RuntimeException> failure) {
        Thread.ofVirtual().start(()->{ try { T value=work.get(); Platform.runLater(()->success.accept(value)); }
            catch(RuntimeException error) { Platform.runLater(()->failure.accept(error)); } });
    }
    static List<City> cities() {
        try(var input=FiscalUi.class.getResourceAsStream("/com/posdesktop/pos/catalogos/municipios-colombia.csv");
            var reader=new BufferedReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).map(s->s.split(";",-1)).filter(a->a.length==4).map(a->new City(a[0],a[1],a[0]+a[2],a[3])).toList();
        } catch(IOException error) { throw new IllegalStateException("No se pudo cargar el catálogo local de municipios."); }
    }
}
