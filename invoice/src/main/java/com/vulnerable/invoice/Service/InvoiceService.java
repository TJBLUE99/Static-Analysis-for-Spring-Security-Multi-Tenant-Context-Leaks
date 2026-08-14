package com.vulnerable.invoice.Service;

import com.vulnerable.invoice.Repository.InvoiceRepository;
import com.vulnerable.invoice.TenantContext.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Async("billingTaskExecutor")
    public void generateInvoicePdf(String invoiceId) {
        String tenantId = TenantContext.getTenantId();

        invoiceRepository.findById(invoiceId, tenantId)
                .ifPresent(invoice -> {
                    byte[] pdf = renderPdf(invoice);
                    invoiceRepository.attachPdf(invoiceId, tenantId, pdf);
                });
    }

    private byte[] renderPdf(Object invoice) {
        return new byte[0]; // stub
    }
}

