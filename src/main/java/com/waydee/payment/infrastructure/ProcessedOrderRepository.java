package com.waydee.payment.infrastructure;

import com.waydee.payment.domain.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/** İşlenmiş sipariş defteri — tekrar eden webhook'ları eler (V42). */
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, String> {
}
