-- V42 · Polar'a geçiş: işlenmiş sipariş defteri — 12 Ağustos 2026
--
-- 🔴 NEDEN: üyelik artık gerçek bir ABONELİK. Her dönem yeni bir `order.paid`
-- gelir ve Polar metadata'yı aboneliğe kopyaladığı için AYNI rezervasyon
-- kimliğiyle gelir. Tekrar koruması rezervasyonun durumundayken (PENDING→PAID)
-- ikinci ayın ödemesi "webhook tekrarı" sanılıp yok sayılırdı: kullanıcı öder,
-- üyeliği uzamaz, süresi dolunca planı düşerdi.
--
-- Doğru idempotency anahtarı SİPARİŞ kimliğidir: aynı sipariş iki kez
-- işlenmez, farklı sipariş yeni bir dönem demektir.
--
-- ⚠️ ON DELETE CASCADE bilinçli: CheckoutService.purgeOldCheckouts 30 günden
-- eski rezervasyonları siler; RESTRICT olsaydı bu temizlik yabancı anahtara
-- takılır ve gecelik iş sessizce hata verirdi.
CREATE TABLE payment_provider_orders (
    order_id     varchar(160) PRIMARY KEY,
    checkout_id  uuid        NOT NULL REFERENCES payment_checkouts (id) ON DELETE CASCADE,
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_orders_checkout ON payment_provider_orders (checkout_id);

COMMENT ON TABLE payment_provider_orders IS
    'İşlenmiş sağlayıcı siparişleri (Polar). Aynı sipariş iki kez plan uzatmaz.';
