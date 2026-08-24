package com.waydee.common.mail;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Waydee'nin giden e-posta şablonları.
 *
 * <p>Tasarım dili Core 2.0'ın <b>açık</b> temasıdır: e-posta istemcilerinin çoğu
 * koyu temayı kendi kurallarıyla uyguladığı için ileti tek temada tasarlanır.
 * Tüm stiller <b>satır içi</b> yazılır — Gmail ve Outlook {@code <style>} bloğunu
 * büyük ölçüde eler.
 *
 * <p>Her şablon HTML'in yanında <b>düz metin</b> karşılığını da üretir
 * (bkz. {@link EmailMessage}).
 */
@Component
public class EmailTemplates {

    /**
     * Kullanıcının gerçekten ulaşabileceği destek adresi.
     *
     * 🔴 İletiler `noreply` adresinden çıkar; metinde "bu e-postayı yanıtla"
     * demek kullanıcıyı boşluğa yönlendirir. Adres tek sabitte tutuluyor ki
     * yeni bir şablon eklendiğinde ikinci bir kopya ayrışmasın.
     */
    private static final String SUPPORT_EMAIL = "info@waydee.com";

    private static final String INK = "#1a1d1f";
    private static final String MUTED = "#6f767e";
    private static final String CANVAS = "#f1f1f1";
    private static final String LINE = "#efefef";

    public EmailMessage verification(String displayName, String url, Duration ttl) {
        String body = paragraph("Merhaba " + escape(displayName) + ",")
                + paragraph("Waydee hesabını oluşturduğun için teşekkürler. Hesabını kullanmaya "
                + "başlamadan önce e-posta adresini doğrulaman gerekiyor.")
                + button("E-postamı doğrula", url)
                + hint("Bu bağlantı " + humanize(ttl) + " geçerlidir.")
                + fallbackLink(url)
                + note("Bu hesabı sen oluşturmadıysan bu e-postayı yok sayabilirsin; "
                + "doğrulanmayan adresle hesap açılmaz.");
        String text = "Merhaba " + displayName + ",\n\n"
                + "Waydee hesabını doğrulamak için aşağıdaki bağlantıyı aç:\n" + url + "\n\n"
                + "Bağlantı " + humanize(ttl) + " geçerlidir.\n"
                + "Bu hesabı sen oluşturmadıysan bu e-postayı yok sayabilirsin.\n\n— Waydee";
        return new EmailMessage("Waydee · E-posta adresini doğrula", layout("E-postanı doğrula", body), text);
    }

    public EmailMessage emailChange(String displayName, String newEmail, String url, Duration ttl) {
        String body = paragraph("Merhaba " + escape(displayName) + ",")
                + paragraph("Waydee hesabının e-posta adresini <strong>" + escape(newEmail)
                + "</strong> olarak değiştirmek istedin. Değişikliğin tamamlanması için yeni adresi doğrula.")
                + button("Yeni adresi doğrula", url)
                + hint("Bu bağlantı " + humanize(ttl) + " geçerlidir.")
                + fallbackLink(url)
                + note("Bağlantıya tıklayana kadar hesabın <strong>eski adresle</strong> çalışmaya devam eder. "
                + "Bu değişikliği sen istemediysen hesabının şifresini hemen değiştir.");
        String text = "Merhaba " + displayName + ",\n\n"
                + "Waydee hesabının e-posta adresini " + newEmail + " olarak değiştirmek için "
                + "aşağıdaki bağlantıyı aç:\n" + url + "\n\n"
                + "Bağlantı " + humanize(ttl) + " geçerlidir. Tıklayana kadar eski adresin geçerlidir.\n\n— Waydee";
        return new EmailMessage("Waydee · Yeni e-posta adresini doğrula", layout("Yeni adresini doğrula", body), text);
    }

    public EmailMessage passwordReset(String displayName, String url, Duration ttl) {
        String body = paragraph("Merhaba " + escape(displayName) + ",")
                + paragraph("Waydee hesabın için şifre sıfırlama isteği aldık. Yeni şifreni "
                + "belirlemek için aşağıdaki düğmeye tıkla.")
                + button("Yeni şifre belirle", url)
                + hint("Güvenlik gereği bu bağlantı yalnızca " + humanize(ttl) + " geçerlidir ve <strong>tek kullanımlıktır</strong>.")
                + fallbackLink(url)
                + note("Bu isteği sen yapmadıysan hiçbir şey yapmana gerek yok — şifren değişmedi.");
        String text = "Merhaba " + displayName + ",\n\n"
                + "Waydee şifreni sıfırlamak için aşağıdaki bağlantıyı aç:\n" + url + "\n\n"
                + "Bağlantı " + humanize(ttl) + " geçerlidir ve tek kullanımlıktır.\n"
                + "Bu isteği sen yapmadıysan şifren değişmedi.\n\n— Waydee";
        return new EmailMessage("Waydee · Şifre sıfırlama", layout("Şifreni sıfırla", body), text);
    }

    /** Şifre sıfırlandıktan sonra hesap sahibine gönderilen bilgilendirme. */
    public EmailMessage passwordChanged(String displayName) {
        String body = paragraph("Merhaba " + escape(displayName) + ",")
                + paragraph("Waydee hesabının şifresi az önce değiştirildi ve <strong>açık olan tüm "
                + "oturumlar kapatıldı</strong>.")
                + note("Bu değişikliği sen yapmadıysan hesabın risk altında olabilir: hemen "
                + "\"şifremi unuttum\" ile yeni bir şifre belirle.");
        String text = "Merhaba " + displayName + ",\n\n"
                + "Waydee hesabının şifresi değiştirildi ve tüm oturumlar kapatıldı.\n"
                + "Bu değişikliği sen yapmadıysan hemen \"şifremi unuttum\" akışını kullan.\n\n— Waydee";
        return new EmailMessage("Waydee · Şifren değiştirildi", layout("Şifren değiştirildi", body), text);
    }

    /** Doğrulama tamamlandıktan sonra gönderilen karşılama. */
    public EmailMessage welcome(String displayName, String appUrl) {
        String body = paragraph("Merhaba " + escape(displayName) + ",")
                + paragraph("E-posta adresin doğrulandı — Waydee'ye hoş geldin. Artık haritada "
                + "kendi daireni çizip bölgeni kiralayabilir, profilini yayına alabilirsin.")
                + button("Haritayı aç", appUrl + "/map")
                /*
                 * 🔴 15 Ağu 2026 — burada "bu e-postayı yanıtlaman yeterli" yazıyordu.
                 * Yanlıştı: bu ileti `noreply` adresinden çıkıyor, yani yanıt kimseye
                 * ulaşmıyor ve destek isteyen kullanıcı sessizce kayboluyordu.
                 * Doğru adres açıkça yazılır ve tıklanabilir olur.
                 */
                + note("Sorun yaşarsan <a href=\"mailto:" + SUPPORT_EMAIL + "\" "
                + "style=\"color:" + INK + ";\">" + SUPPORT_EMAIL + "</a> adresine yazabilirsin.");
        String text = "Merhaba " + displayName + ",\n\n"
                + "E-posta adresin doğrulandı, Waydee'ye hoş geldin.\n"
                + "Haritayı aç: " + appUrl + "/map\n\n"
                + "Sorun yaşarsan " + SUPPORT_EMAIL + " adresine yazabilirsin.\n\n— Waydee";
        return new EmailMessage("Waydee'ye hoş geldin", layout("Hoş geldin", body), text);
    }

    // ------------------------------------------------------------------ parçalar

    private String layout(String heading, String body) {
        return """
                <!doctype html>
                <html lang="tr"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:%s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background:%s;padding:32px 12px;">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                           style="max-width:520px;background:#ffffff;border-radius:24px;
                                  font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Inter,Arial,sans-serif;">
                      <tr><td style="padding:32px 32px 8px 32px;">
                        <div style="font-size:20px;font-weight:700;letter-spacing:-0.02em;color:%s;">Waydee</div>
                      </td></tr>
                      <tr><td style="padding:8px 32px 0 32px;">
                        <h1 style="margin:0 0 16px 0;font-size:24px;line-height:1.25;font-weight:700;
                                   letter-spacing:-0.02em;color:%s;">%s</h1>
                      </td></tr>
                      <tr><td style="padding:0 32px 32px 32px;">%s</td></tr>
                      <tr><td style="padding:0 32px 32px 32px;">
                        <div style="border-top:1px solid %s;padding-top:16px;font-size:12px;color:%s;">
                          Bu ileti Waydee tarafından otomatik gönderildi.
                        </div>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(escape(heading), CANVAS, CANVAS, INK, INK, escape(heading), body, LINE, MUTED);
    }

    private String paragraph(String html) {
        return "<p style=\"margin:0 0 16px 0;font-size:15px;line-height:1.6;color:" + INK + ";\">" + html + "</p>";
    }

    private String button(String label, String url) {
        // Pill buton — Core 2.0'ın `bg-ink` birincil düğmesinin e-posta karşılığı.
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:24px 0;\"><tr><td style=\"border-radius:999px;background:" + INK + ";\">"
                + "<a href=\"" + escape(url) + "\" style=\"display:inline-block;padding:14px 28px;"
                + "font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:999px;\">"
                + escape(label) + "</a></td></tr></table>";
    }

    private String hint(String html) {
        return "<p style=\"margin:0 0 16px 0;font-size:13px;line-height:1.6;color:" + MUTED + ";\">" + html + "</p>";
    }

    /** Düğme çalışmayan istemciler için ham adres. */
    private String fallbackLink(String url) {
        return "<p style=\"margin:0 0 16px 0;font-size:12px;line-height:1.6;color:" + MUTED + ";"
                + "word-break:break-all;\">Düğme çalışmıyorsa bu adresi tarayıcına yapıştır:<br>"
                + "<span style=\"color:" + INK + ";\">" + escape(url) + "</span></p>";
    }

    private String note(String html) {
        return "<div style=\"margin-top:8px;padding:14px 16px;background:#f4f4f4;border-radius:16px;"
                + "font-size:13px;line-height:1.6;color:" + MUTED + ";\">" + html + "</div>";
    }

    /** "24 saat" / "60 dakika" gibi okunur süre — TTL ayarı değişince metin de değişir. */
    private String humanize(Duration ttl) {
        long hours = ttl.toHours();
        if (hours >= 1) {
            return hours + " saat";
        }
        return Math.max(1, ttl.toMinutes()) + " dakika";
    }

    /**
     * Kullanıcıdan gelen değerler (ad, e-posta) HTML gövdeye girer — kaçırılmazsa
     * ileti gövdesine etiket enjekte edilebilir.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
