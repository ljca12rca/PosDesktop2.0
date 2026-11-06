package com.posdesktop.pos.fiscal;
import com.posdesktop.pos.integration.PosApiClient;
import java.math.BigDecimal;
import javafx.beans.property.*;

/** Estado de edicion de una fila; los calculos finales se solicitan al servidor. */
final class AdjustmentEditorRow {
    final PosApiClient.AdjustmentLine source;
    final ObjectProperty<BigDecimal> quantity=new SimpleObjectProperty<>(BigDecimal.ZERO);
    final ObjectProperty<BigDecimal> increase=new SimpleObjectProperty<>(BigDecimal.ZERO);
    AdjustmentEditorRow(PosApiClient.AdjustmentLine source){this.source=source;}
    PosApiClient.AdjustmentSelection selection(){return new PosApiClient.AdjustmentSelection(source.order(),quantity.get(),increase.get());}
}
