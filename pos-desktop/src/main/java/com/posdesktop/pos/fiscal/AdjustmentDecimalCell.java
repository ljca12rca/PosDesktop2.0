package com.posdesktop.pos.fiscal;
import java.math.BigDecimal;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

/** Enter confirma, Escape revierte; perder foco confirma solo un numero completo y valido. */
final class AdjustmentDecimalCell extends TableCell<AdjustmentEditorRow,BigDecimal> {
    private final Consumer<String> error;
    private final Runnable next;
    private TextField editor;
    AdjustmentDecimalCell(Consumer<String> error,Runnable next){this.error=error;this.next=next;}
    @Override public void startEdit(){if(isEmpty())return;super.startEdit();if(!isEditing())return;
        editor=new TextField(getItem()==null?"0":getItem().stripTrailingZeros().toPlainString());editor.getStyleClass().add("soft-field");editor.setId("adjustment-decimal-editor");editor.setAccessibleText("Cantidad o incremento del artículo");
        editor.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==KeyCode.ENTER){try{var v=parse(editor.getText());commitEdit(v);next.run();}catch(IllegalArgumentException ex){error.accept(ex.getMessage());editor.requestFocus();}e.consume();}
            else if(e.getCode()==KeyCode.ESCAPE){cancelEdit();getTableView().requestFocus();e.consume();}});
        editor.focusedProperty().addListener((o,was,focused)->{if(!focused&&isEditing())try{commitEdit(parse(editor.getText()));}catch(IllegalArgumentException ex){error.accept(ex.getMessage());cancelEdit();}});
        setText(null);setGraphic(editor);Platform.runLater(()->{editor.requestFocus();editor.selectAll();});
    }
    static BigDecimal parse(String text){String n=text==null?"":text.strip().replace(',','.');if(!n.matches("[0-9]+(?:\\.[0-9]{1,6})?"))throw new IllegalArgumentException("Usa un número positivo o cero, con coma o punto y hasta seis decimales.");return new BigDecimal(n);}
    @Override public void cancelEdit(){super.cancelEdit();setGraphic(null);setText(display(getItem()));}
    @Override public void updateItem(BigDecimal value,boolean empty){super.updateItem(value,empty);if(empty){setText(null);setGraphic(null);}else if(!isEditing()){setText(display(value));setGraphic(null);}}
    private static String display(BigDecimal value){return value==null?"":value.stripTrailingZeros().toPlainString();}
}
