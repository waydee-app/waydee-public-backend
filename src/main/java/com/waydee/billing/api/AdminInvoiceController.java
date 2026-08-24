package com.waydee.billing.api;

import com.waydee.billing.api.dto.InvoiceDtos.InvoiceDetail;
import com.waydee.billing.api.dto.InvoiceDtos.InvoiceSummary;
import com.waydee.billing.application.InvoiceService;
import com.waydee.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Yönetim: tüm faturalar. {@code /api/v1/admin/**} zaten ADMIN rolüne kapalıdır. */
@Tag(name = "Admin · Invoices", description = "Fatura yönetimi")
@RestController
@RequestMapping("/api/v1/admin/invoices")
@RequiredArgsConstructor
public class AdminInvoiceController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceService invoiceService;

    @Operation(summary = "Faturalar (fatura no / kullanıcı adı / ad ile aranabilir)")
    @GetMapping
    public PageResponse<InvoiceSummary> list(@RequestParam(required = false) String query,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return invoiceService.adminList(query,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "issuedAt")));
    }

    @Operation(summary = "Muhasebe panosu — ciro, KDV, para birimi kırılımı, grafik, en çok harcayanlar")
    @GetMapping("/accounting")
    public com.waydee.billing.api.dto.InvoiceDtos.AccountingSummary accounting(
            @RequestParam(defaultValue = "30") int days) {
        return invoiceService.accounting(days);
    }

    @Operation(summary = "Fatura detayı")
    @GetMapping("/{id}")
    public InvoiceDetail get(@PathVariable UUID id) {
        return invoiceService.adminGet(id);
    }
}
