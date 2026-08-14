package com.vulnerable.invoice.Controller;

import com.vulnerable.invoice.Service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {
    private final InvoiceService invoiceService;

    Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/api/v1/invoices/{invoiceId}/generate")
    public String generate(@PathVariable String invoiceId) {
        logger.info("Invoice controller");
        invoiceService.generateInvoicePdf(invoiceId);
        return "queued";
    }
}
