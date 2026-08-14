package com.vulnerable.invoice.Repository;


import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InvoiceRepository {


    public Optional<Invoice> findById(String invoiceId, String tenantId) {

        return Optional.of(new Invoice(invoiceId, tenantId));
    }

    public void attachPdf(String invoiceId, String tenantId, byte[] pdf) {

    }

    public static class Invoice {
        private final String invoiceId;
        private final String tenantId;

        public Invoice(String invoiceId, String tenantId) {
            this.invoiceId = invoiceId;
            this.tenantId = tenantId;
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public String getTenantId() {
            return tenantId;
        }
    }
}
