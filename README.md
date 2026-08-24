<div align="center">

# Waydee · Backend

**Haritayı sahiplenilebilir bir sosyal tuvale çeviren platformun sunucu tarafı.**

Spring Boot 3.5 · Java 21 · PostgreSQL + PostGIS · Redis · S3 uyumlu depo

`397 Java dosyası` — `17 modül` — `54 veritabanı göçü` — `5 dil`

</div>

> [!WARNING]
> **Bu bir vitrin kopyasıdır — çalıştırılamaz.**
> Depo yalnızca **inceleme** amacıyla yayınlanmıştır. Yapı dosyaları
> (`pom.xml`, Maven sarmalayıcısı, `Dockerfile`, CI tanımları) ve kimlik
> doğrulama · imzalama · ödeme · sır yönetimiyle ilgili **17 sınıfın içeriği
> bilerek çıkarılmıştır**. Ayrıntı: [Kaldırılanlar](#-kaldırılanlar).

---

## Ürün

Kullanıcı kaydolur ve kendine bir **vitrin adresi** alır
(`waydee.com/kullaniciadi`). Fotoğraf paylaşır, fotoğrafın üstüne **ürün
etiketi** koyar; ziyaretçiler etikete dokunup satın alma bağlantısına gider.
Ücretli üye ayrıca **haritada 100 metrelik bir mağaza** açar — haritadaki
işaretçiye tıklayan kişi doğrudan o kullanıcının profiline iner.

Yani harita bir dekor değil: **keşfin kendisi**.

---

## Mimari

### Modüler monolit

Tek deploy edilir, ama modüllere ayrılmıştır. Her modülün kendi
`api / application / domain / infrastructure` katmanı vardır ve **modüller
birbirinin repository'sine dokunmaz** — yalnızca application servisleri ve
domain olayları üzerinden konuşur.

```
com.waydee
├── common          shared kernel — denetim, güvenlik, hata, depolama,
│                   geo, olaylar, hız sınırlama, e-posta
├── identity        kullanıcı, kayıt/giriş, JWT, refresh rotasyonu, plan, kredi
├── geo             ülke/il/ilçe + fiyat bölgeleri + fiyat çözümleme
├── territory       alan doğrulama, mağaza kurma, sahiplik, kategoriler
├── social          profil, medya, gönderi/beğeni/yorum, hikâye, ölçüm
├── marketplace     pazar yerleri, başvurular, 3B vitrin
├── messaging       mesajlaşma ve mesaj istekleri
├── moderation      şikâyet ve kısıtlama
├── monetization    gelir başvuruları
├── billing         değişmez fatura + indirim kuponları
├── payment         ödeme oturumu + rezervasyon (sağlayıcı portu, webhook)
├── aistudio        yapay zekâ görsel üretimi ve kredi ekonomisi
├── realtime        domain olayı → STOMP köprüsü (WebSocket)
├── traffic         trafik kontrolü ve ölçüm
├── publicview      vitrin okuma uçları — TEK kimliksiz veri yüzeyi
├── admin           yönetim konsolu uçları
└── bootstrap       açılışta idempotent kurulum
```

### Katman kuralları

| Katman | Sorumluluk |
|---|---|
| `api` | Controller + DTO. **Asla entity döndürmez**; DTO'lar `from()` fabrikasıyla üretilir. |
| `application` | İş kuralları, `@Transactional` sınırı |
| `domain` | Entity ve değer nesneleri |
| `infrastructure` | JPA repository, dış istemciler |

### Gerçek zamanlı katman — soyutlanmış

İş mantığı bir arayüze olay basar; hangi teknolojiyle taşındığını **bilmez**.

```
PurchaseService.purchase()
  └─ eventPublisher.publish(TerritoryPurchasedEvent)      ← soyut arayüz
       └─ SpringDomainEventPublisher (süreç içi)
            └─ TerritoryRealtimeBroadcaster @TransactionalEventListener(AFTER_COMMIT)
                 └─ SimpMessagingTemplate → /topic/territories
```

Redis Pub/Sub ya da Kafka'ya geçiş, bu arayüzün yeni bir uygulamasıdır.
**İş mantığı değişmez.**

### Ödeme — üç adımlı ve asenkron

Sahte bir geçit "anında başarılı" der; gerçek sağlayıcılar demez. Akış
sunucunun **ödeme oturumu açması** → kullanıcının o sayfada ödemesi →
sonucun **imzalı webhook** ile dönmesidir.

Asenkronluk bir **rezervasyon** gerektirdi: ödeme başlarken alan geometrisiyle
tutulur, süresi dolarsa serbest kalır. Aynı ilke kuponda da geçerlidir
(`RESERVED → CONFIRMED / RELEASED`).

---

## Öne çıkan mühendislik kararları

<table>
<tr><td width="30%"><b>Kategoriler enum değil tablo</b></td>
<td>Enumda her değer <b>kodda ayrı bir yol</b> demektir. Kategori öyle değil —
yeni bir kategori yalnızca bir satırdır. Enum olsaydı her kategori bir göç +
yeniden dağıtım olurdu. Silme yerine <b>pasife alma</b>: silmek, o kategoriyi
seçmiş mağazaların referansını da götürürdü.</td></tr>

<tr><td><b>Görüntülenme tekilliği şemada</b></td>
<td>"Aynı kişi günde bir kez sayılsın" kuralı uygulamada bir kontrol olarak
duruyordu; iki sekme arasındaki <b>yarışta</b> sessizce iki satır yazıyordu.
Kural <code>UNIQUE (territory_id, viewer_id, view_day)</code> ile veritabanına
taşındı — doğruluk artık zamanlamaya bağlı değil.</td></tr>

<tr><td><b>Hız sınırı ve gerçek IP</b></td>
<td><code>X-Forwarded-For</code> zinciri <b>sağdan</b> sayılır. En soldaki
girdiyi istemci yazar; onu okumak hız sınırının tamamen atlanabilmesi
demekti. Güvenilen vekil sayısı yapılandırmadan gelir.</td></tr>

<tr><td><b>Bağlantı önizlemesinde SSRF eleği</b></td>
<td>Özel ağlar, <b>CGNAT</b> (<code>100.64/10</code>), bulut metadata
(<code>169.254/16</code>) ve eşlemeli IPv4 adresleri elenir.</td></tr>

<tr><td><b>Hesap silme = anonimleştirme</b></td>
<td>Faturalar, satın almalar ve gönderiler <code>RESTRICT</code> ile bağlı.
Gerçek bir <code>DELETE</code> ya hata verir ya da CASCADE ile <b>mali
kayıtları yok eder</b>. Aynı ilke mağaza silmede de geçerli: satır silinmez,
<code>REVOKED</code>'a düşer.</td></tr>

<tr><td><b>Harita sorgusuna JOIN eklenmez</b></td>
<td>GeoJSON döngüsü bütün mağazaları dolaşır. Kategori bir ilişki olsaydı her
satırda ya bir sorgu daha (N+1) ya bir JOIN daha olurdu; liste <b>bir kez</b>
okunup bir haritaya çevriliyor.</td></tr>
</table>

---

## Veri katmanı

- **PostgreSQL 16 + PostGIS** — daireler gerçek geometridir
  (`geometry(Polygon,4326)`); çakışma kontrolü ve alan hesabı veritabanında yapılır.
- **Flyway** — 54 göç, hepsi ileri yönlü. Geçmiş değişmez: geri alınan bir
  özelliğin göçü **yerinde durur**, temizlik yeni bir göçle yapılır.
- **Redis** — hız sınırlama ve giriş kilidi.
- **S3 uyumlu depo** — medya; erişim **imzalı ve süreli** adreslerle.

Göç dosyaları yalnızca şema değil, **kararın gerekçesini** de taşır —
`src/main/resources/db/migration/` altına bakmanızı öneririz.

---

## 🔒 Kaldırılanlar

Aşağıdaki sınıfların **içeriği** çıkarılmış, yerine ne yaptıklarını anlatan
birer taslak konmuştur (toplam ~**1.970 satır**):

| Alan | Sınıflar |
|---|---|
| Güvenlik | `SecurityConfig` · `JwtService` · `JwtAuthenticationFilter` · `ProductionSecretsGuard` |
| İmzalama | `MediaUrlSigner` · `OAuthStateSigner` · `VisitorPassService` |
| Kimlik | `TokenService` · `GoogleAuthService` · `GoogleOAuthClient` · `TurnstileService` |
| Ödeme | `PolarProvider` · `MockPaymentProvider` |
| Kurulum | `SeedService` |

Ayrıca **tüm yapı ve dağıtım dosyaları** (`pom.xml`, Maven sarmalayıcısı,
`Dockerfile`, `.github/`) ve **tüm ortam dosyaları** (`.env`, `.env.example`)
kaldırılmış; `application.yml` içindeki geliştirme varsayılanları
(veritabanı/Redis/depo şifreleri, geliştirme JWT anahtarı) **boşaltılmıştır**.

> Depoda hiçbir gizli anahtar, şifre veya jeton bulunmamaktadır.

---

<div align="center">
<sub>Waydee · inceleme kopyası · üretim kaynağı özel bir depoda tutulur</sub>
</div>
