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

    @Value("${SPRING_MAIL_PASSWORD:}")
    private String brevoApiKey;

    public void sendOtpEmail(String toEmail, String otp, String userName) {
        // 1. Try Brevo HTTP API first (port 443, never blocked on VPS)
        if (sendEmailViaBrevoApi(toEmail, "Mã xác minh đặt lại mật khẩu – TTVV", buildOtpEmailHtml(userName, otp))) {
            return;
        }

        // 2. Fallback to traditional SMTP (Gmail)
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Mã xác minh đặt lại mật khẩu – TTVV");
            helper.setText(buildOtpEmailHtml(userName, otp), true);

            mailSender.send(message);
            log.info("✅ OTP email sent successfully via SMTP. To: {}", toEmail);

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("❌ Failed to send OTP email via SMTP to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }
    }

    private boolean sendEmailViaBrevoApi(String to, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.debug("ℹ️ Brevo API Key is not configured, skipping HTTP email sending.");
            return false;
        }
        try {
            String escapedHtml = htmlContent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

            String json = String.format(
                "{\"sender\":{\"name\":\"%s\",\"email\":\"%s\"},\"to\":[{\"email\":\"%s\"}],\"subject\":\"%s\",\"htmlContent\":\"%s\"}",
                fromName.replace("\"", "\\\""),
                fromEmail.replace("\"", "\\\""),
                to,
                subject.replace("\\", "\\\\").replace("\"", "\\\""),
                escapedHtml
            );

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", brevoApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✅ OTP email sent successfully via Brevo HTTP API. To: {}", to);
                return true;
            } else {
                log.warn("⚠️ Brevo HTTP API returned status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to send OTP email via Brevo HTTP API: {}", e.getMessage());
            return false;
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
                          <p style="margin:4px 0 0;font-size:13px;color:#bfdbfe;">Nền tảng mạng xã hội doanh nghiệp</p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:40px 40px 24px;">
                          <p style="margin:0 0 8px;font-size:16px;color:#374151;">Xin chào, <strong>%s</strong></p>
                          <p style="margin:0 0 24px;font-size:15px;color:#6b7280;line-height:1.6;">
                            Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.<br>
                            Vui lòng sử dụng mã xác minh bên dưới để tiếp tục.
                          </p>

                          <!-- OTP Box -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                            <tr>
                              <td align="center">
                                <div style="display:inline-block;background:#eff6ff;border:2px solid #bfdbfe;border-radius:12px;padding:20px 48px;">
                                  <p style="margin:0 0 4px;font-size:12px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:1.5px;">Mã xác minh</p>
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
                                  ⏱ Mã có hiệu lực trong <strong>15 phút</strong>.<br>
                                  🔒 Không chia sẻ mã này với bất kỳ ai, kể cả nhân viên TTVV.<br>
                                  ✉ Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
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
                            Email này được gửi tự động từ hệ thống <strong>TTVV Business</strong>.<br>
                            Vui lòng không trả lời email này. Nếu cần hỗ trợ, liên hệ
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
