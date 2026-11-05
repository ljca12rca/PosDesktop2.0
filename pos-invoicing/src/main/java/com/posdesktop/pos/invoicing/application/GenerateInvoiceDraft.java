package com.posdesktop.pos.invoicing.application;

import com.posdesktop.pos.invoicing.domain.FiscalInvoice;

/** Solo previsualizacion; no contiene puerto de transporte ni repositorio de consecutivos. */
public interface GenerateInvoiceDraft {
    record Draft(String xml, ProviderType provider, State state) {
        public enum State { LOCAL_DRAFT_NOT_ISSUED }
    }
    Draft generate(FiscalInvoice invoice);
}
