package edu.iuh.fit.se.authservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from:TTVV@Business.com.vn}")
    private String fromEmail;

    @Value("${mail.from-name:TTVV Business}")
    private String fromName;

    public void sendOtpEmail(String toEmail, String otp, String userName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("MÃ£ xÃ¡c minh Ä‘áº·t láº¡i máº­t kháº©u â€” TTVV");
            helper.setText(buildOtpEmailHtml(userName, otp), true);

            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }
    }

    private String buildOtpEmailHtml(String userName, String otp) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background:#f4f6f9;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f9;padding:40px 0;">
                <tr>
                  <td align="center">
                    <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                      <!-- Header -->
                      <tr>
                        <td style="background:#1a56db;padding:32px 40px;">
                          <p style="margin:0;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:0.5px;">TTVV Business</p>
                          <p style="margin:4px 0 0;font-size:13px;color:#bfdbfe;">Ná»n táº£ng máº¡ng xÃ£ há»™i doanh nghiá»‡p</p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:40px 40px 24px;">
                          <p style="margin:0 0 8px;font-size:16px;color:#374151;">Xin chÃ o, <strong>%s</strong></p>
                          <p style="margin:0 0 24px;font-size:15px;color:#6b7280;line-height:1.6;">
                            ChÃºng tÃ´i nháº­n Ä‘Æ°á»£c yÃªu cáº§u Ä‘áº·t láº¡i máº­t kháº©u cho tÃ i khoáº£n cá»§a báº¡n.<br>
                            Vui lÃ²ng sá»­ dá»¥ng mÃ£ xÃ¡c minh bÃªn dÆ°á»›i Ä‘á»ƒ tiáº¿p tá»¥c.
                          </p>

                          <!-- OTP Box -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                            <tr>
                              <td align="center">
                                <div style="display:inline-block;background:#eff6ff;border:2px solid #bfdbfe;border-radius:12px;padding:20px 48px;">
                                  <p style="margin:0 0 4px;font-size:12px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:1.5px;">MÃ£ xÃ¡c minh</p>
                                  <p style="margin:0;font-size:40px;font-weight:800;color:#1a56db;letter-spacing:12px;font-family:'Courier New',monospace;">%s</p>
                                </div>
                              </td>
                            </tr>
                          </table>

                          <!-- Notice -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#fefce8;border-left:4px solid #f59e0b;border-radius:0 6px 6px 0;margin:0 0 24px;">
                            <tr>
                              <td style="padding:14px 16px;">
                                <p style="margin:0;font-size:13px;color:#92400e;line-height:1.5;">
                                  â± MÃ£ cÃ³ hiá»‡u lá»±c trong <strong>15 phÃºt</strong>.<br>
                                  ðŸ”’ KhÃ´ng chia sáº» mÃ£ nÃ y vá»›i báº¥t ká»³ ai, ká»ƒ cáº£ nhÃ¢n viÃªn TTVV.<br>
                                  âœ‰ Náº¿u báº¡n khÃ´ng yÃªu cáº§u Ä‘áº·t láº¡i máº­t kháº©u, hÃ£y bá» qua email nÃ y.
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Divider -->
                      <tr>
                        <td style="padding:0 40px;">
                          <hr style="border:none;border-top:1px solid #e5e7eb;margin:0;">
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="padding:20px 40px 32px;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.6;">
                            Email nÃ y Ä‘Æ°á»£c gá»­i tá»± Ä‘á»™ng tá»« há»‡ thá»‘ng <strong>TTVV Business</strong>.<br>
                            Vui lÃ²ng khÃ´ng tráº£ lá»i email nÃ y. Náº¿u cáº§n há»— trá»£, liÃªn há»‡
                            <a href="mailto:TTVV@Business.com.vn" style="color:#1a56db;text-decoration:none;">TTVV@Business.com.vn</a>.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(userName, otp);
    }
}
