package com.posdesktop.pos;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.CheckBox;

/** Intencion por venta: desmarcar cancela; volver a marcar no revive una impresion anterior. */
final class ElectronicPrintRequest implements BooleanSupplier, AutoCloseable {
    private final CheckBox check;
    private final AtomicBoolean allowed;
    private boolean changed;
    private final ChangeListener<Boolean> listener;

    ElectronicPrintRequest(CheckBox check) {
        this.check = check;
        allowed = new AtomicBoolean(check.isSelected());
        listener = (observable, previous, selected) -> {
            changed = true;
            if (!selected) allowed.set(false);
        };
        check.selectedProperty().addListener(listener);
        check.getProperties().put(ElectronicPrintRequest.class, this);
    }

    @Override public boolean getAsBoolean() { return allowed.get(); }

    // Invocar desde JavaFX al terminar, sin alterar la preferencia de otra venta.
    @Override public void close() {
        check.selectedProperty().removeListener(listener);
        if (check.getProperties().get(ElectronicPrintRequest.class) == this) {
            check.getProperties().remove(ElectronicPrintRequest.class);
            if (!changed) check.setSelected(false);
        }
    }
}
