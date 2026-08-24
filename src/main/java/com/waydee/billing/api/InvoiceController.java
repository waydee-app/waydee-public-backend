package com.waydee.billing.api;

import com.waydee.billing.api.dto.InvoiceDtos.BillingSummary;
import com.waydee.billing.api.dto.InvoiceDtos.InvoiceDetail;
import com.waydee.billing.api.dto.InvoiceDtos.InvoiceSummary;
import com.waydee.billing.application.InvoiceService;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Invoices", description = "Kullanıcının faturaları")
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private static final int MAX_PAGE_SIZE = 50;

    private final InvoiceService invoiceService;

    @Operation(summary = "Faturalarım (en yeni önce)")
    @GetMapping
    public PageResponse<InvoiceSummary> myInvoices(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return invoiceService.myInvoices(principal.id(), PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    @Operation(summary = "Fatura özeti (adet + toplam harcama)")
    @GetMapping("/summary")
    public BillingSummary summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        return invoiceService.summary(principal.id());
    }

    @Operation(summary = "Fatura detayı (yalnız kendi faturan)")
    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return invoiceService.myInvoice(id, principal.id());
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
