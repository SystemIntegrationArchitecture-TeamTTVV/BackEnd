package edu.iuh.fit.se.commonservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from:thachtaro123@gmail.com}")
    private String fromEmail;

    @Value("${mail.from-name:TTVV Business}")
    private String fromName;

    @Value("${app.base-url:http://localhost:5311}")
    private String baseUrl;

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${RESEND_FROM_EMAIL:onboarding@resend.dev}")
    private String resendFromEmail;

    @Value("${SPRING_MAIL_PASSWORD:}")
    private String brevoApiKey;

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken, String userName) {
        String resetLink = baseUrl + "/auth/reset-password?token=" + resetToken;
        String htmlContent = buildPasswordResetEmailHtml(userName, resetLink);
        String subject = "Reset Your Password - TTVV Social Network";

        // 1. Try Resend HTTP API first (port 443, never blocked)
        if (sendEmailViaResend(toEmail, subject, htmlContent)) {
            return;
        }

        // 2. Try Brevo HTTP API next (port 443, never blocked)
        if (sendEmailViaBrevoApi(toEmail, subject, htmlContent)) {
            return;
        }

        // 3. Fallback to traditional SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Password reset email sent successfully via SMTP. To: {}", toEmail);

        } catch (Exception e) {
            log.warn("⚠️ Failed to send password reset email via SMTP (SMTP server unreachable or port blocked): {}. Proceeding without breaking the flow.", e.getMessage());
            // Safe fallback: do not throw RuntimeException so registration/reset flow doesn't break
        }
    }

    private String buildPasswordResetEmailHtml(String userName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #ffffff; padding: 40px 30px; border: 1px solid #e0e0e0; }
                    .button { display: inline-block; padding: 14px 40px; background: #667eea; color: white !important; text-decoration: none; border-radius: 8px; font-weight: 600; margin: 20px 0; }
                    .button:hover { background: #5568d3; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 14px; }
                    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 4px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1 style="margin: 0;">🔐 Password Reset Request</h1>
                    </div>
                    <div class="content">
                        <p>Hi <strong>%s</strong>,</p>
                        <p>We received a request to reset your password for your TTVV Social Network account.</p>
                        <p>Click the button below to reset your password:</p>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Reset Password</a>
                        </div>
                        <div class="warning">
                            <strong>⚠️ Security Notice:</strong>
                            <ul style="margin: 10px 0; padding-left: 20px;">
                                <li>This link will expire in 1 hour</li>
                                <li>If you didn't request this, please ignore this email</li>
                                <li>Never share this link with anyone</li>
                            </ul>
                        </div>
                        <p style="color: #666; font-size: 14px; margin-top: 30px;">
                            Or copy and paste this link into your browser:<br>
                            <a href="%s" style="color: #667eea; word-break: break-all;">%s</a>
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2026 TTVV Social Network. All rights reserved.</p>
                        <p style="font-size: 12px; color: #999;">This is an automated email. Please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, resetLink, resetLink, resetLink);
    }

    public void sendLivestreamQuotationEmail(String toEmail, String userName, String packageName, String userId) {
        String htmlContent = buildLivestreamQuotationEmailHtml(userName, packageName, userId);
        String subject = "Xác nhận đăng ký dịch vụ Livestream - TTVV";

        // 1. Try Resend HTTP API first (port 443, never blocked)
        if (sendEmailViaResend(toEmail, subject, htmlContent)) {
            return;
        }

        // 2. Try Brevo HTTP API next (port 443, never blocked)
        if (sendEmailViaBrevoApi(toEmail, subject, htmlContent)) {
            return;
        }

        // 3. Fallback to traditional SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Livestream quotation email sent successfully via SMTP. To: {}", toEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send livestream quotation email via SMTP: {}", e.getMessage());
        }
    }

    private String buildLivestreamQuotationEmailHtml(String userName, String packageName, String userId) {
        String price = "3.500.000đ/tháng";
        if (packageName != null) {
            String lower = packageName.toLowerCase();
            if (lower.contains("cơ bản")) price = "1.500.000đ/tháng";
            else if (lower.contains("doanh nghiệp")) price = "8.000.000đ/tháng";
        }
        
        String checkoutUrl = baseUrl + "/livestream/vip-packages?userId=" + (userId != null ? userId : "");
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 20px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; }
                    .button { display: inline-block; padding: 12px 30px; background: #667eea; color: white !important; text-decoration: none; border-radius: 5px; font-weight: bold; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2 style="margin: 0;">🎉 Xác nhận đăng ký dịch vụ Livestream</h2>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Cảm ơn bạn đã quan tâm và đăng ký dịch vụ Livestream của hệ thống TTVV.</p>
                        <p>Dưới đây là thông tin gói dịch vụ bạn đã chọn qua cuộc gọi AI tư vấn:</p>
                        <ul style="background: #f9f9f9; padding: 15px 30px; border-radius: 5px;">
                            <li><strong>Tên gói:</strong> %s</li>
                            <li><strong>Chi phí:</strong> %s</li>
                        </ul>
                        <p>Để hoàn tất đăng ký và kích hoạt dịch vụ, vui lòng thanh toán và điền thông tin tại liên kết dưới đây:</p>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Thanh toán & Kích hoạt ngay</a>
                        </div>
                        <p>Nếu bạn có bất kỳ câu hỏi nào, vui lòng trả lời trực tiếp email này.</p>
                        <p>Trân trọng,<br>Đội ngũ TTVV.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName != null ? userName : "Quý khách", 
                          packageName != null ? packageName : "Gói Livestream", 
                          price, checkoutUrl);
    }

    public void sendConsultationInvitationEmail(String toEmail, String userName, String webClientUrl) {
        String htmlContent = buildConsultationInvitationEmailHtml(userName, webClientUrl);
        String subject = "Lời mời tư vấn giải pháp Livestream AI - TTVV";

        // 1. Try Resend HTTP API first (port 443, never blocked)
        if (sendEmailViaResend(toEmail, subject, htmlContent)) {
            return;
        }

        // 2. Try Brevo HTTP API next (port 443, never blocked)
        if (sendEmailViaBrevoApi(toEmail, subject, htmlContent)) {
            return;
        }

        // 3. Fallback to traditional SMTP
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Invitation email sent successfully via SMTP. To: {}", toEmail);

        } catch (Exception e) {
            log.warn("⚠️ Failed to send invitation email via SMTP (SMTP server unreachable or port blocked): {}. Proceeding without breaking the flow.", e.getMessage());
            // Safe fallback: do not throw RuntimeException so the main consultation flow remains functional
        }
    }

    private String buildConsultationInvitationEmailHtml(String userName, String webClientUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 35px 20px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: #ffffff; padding: 30px; border: 1px solid #e0e0e0; }
                    .button { display: inline-block; padding: 14px 35px; background: #667eea; color: white !important; text-decoration: none; border-radius: 8px; font-weight: bold; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2 style="margin: 0; font-size: 24px;">💬 Lời mời trải nghiệm Tư vấn AI</h2>
                    </div>
                    <div class="content">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Chúng tôi trân trọng kính mời bạn tham gia buổi tư vấn giải pháp và đăng ký gói Livestream chuyên nghiệp cùng <strong>Trợ lý AI TTVV</strong>.</p>
                        <p>Trợ lý AI thông minh sẽ trực tiếp lắng nghe yêu cầu, trao đổi và gợi ý gói dịch vụ tối ưu nhất dành riêng cho nhu cầu của bạn hoàn toàn tự động qua giọng nói và tin nhắn.</p>
                        
                        <div style="text-align: center;">
                            <a href="%s" class="button">Bắt đầu Trò chuyện ngay</a>
                        </div>
                        
                        <p style="background: #f9f9f9; padding: 15px; border-left: 4px solid #667eea; border-radius: 4px; font-size: 14px; color: #555;">
                            <strong>💡 Hướng dẫn nhanh:</strong><br>
                            1. Nhấp vào nút ở trên để mở trang trợ lý.<br>
                            2. Cho phép thiết bị truy cập Microphone khi được yêu cầu.<br>
                            3. Bấm vào <strong>Quả cầu năng lượng AI</strong> để bắt đầu trò chuyện trực tiếp bằng giọng nói!
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2026 TTVV Social Network. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName != null ? userName : "Quý khách", webClientUrl);
    }

    private boolean sendEmailViaResend(String to, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.debug("ℹ️ Resend API Key is not configured, skipping HTTP email sending.");
            return false;
        }
        try {
            String from = (resendFromEmail != null && !resendFromEmail.isBlank()) 
                ? resendFromEmail 
                : "onboarding@resend.dev";
                
            // Clean up htmlContent quotes and linebreaks to make a safe JSON string
            String escapedHtml = htmlContent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

            String json = String.format(
                "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"%s\",\"html\":\"%s\"}",
                from,
                to,
                subject.replace("\\", "\\\\").replace("\"", "\\\""),
                escapedHtml
            );

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✅ Email sent successfully via Resend HTTP API. To: {}", to);
                return true;
            } else {
                log.warn("⚠️ Resend HTTP API returned status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to send email via Resend HTTP API: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendEmailViaBrevoApi(String to, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.debug("ℹ️ Brevo API Key is not configured, skipping HTTP email sending.");
            return false;
        }
        try {
            // Clean up htmlContent quotes and linebreaks to make a safe JSON string
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
                log.info("✅ Email sent successfully via Brevo HTTP API. To: {}", to);
                return true;
            } else {
                log.warn("⚠️ Brevo HTTP API returned status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to send email via Brevo HTTP API: {}", e.getMessage());
            return false;
        }
    }
}
