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

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken, String userName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String resetLink = baseUrl + "/auth/reset-password?token=" + resetToken;
            String htmlContent = buildPasswordResetEmailHtml(userName, resetLink);

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - TTVV Social Network");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Password reset email sent successfully via SMTP. To: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("❌ Failed to send password reset email via SMTP: {}", e.getMessage());
            throw new RuntimeException("Failed to send reset email: " + e.getMessage());
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String htmlContent = buildLivestreamQuotationEmailHtml(userName, packageName, userId);

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Xác nhận đăng ký dịch vụ Livestream - TTVV");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Livestream quotation email sent successfully via SMTP. To: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String htmlContent = buildConsultationInvitationEmailHtml(userName, webClientUrl);

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Lời mời tư vấn giải pháp Livestream AI - TTVV");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Invitation email sent successfully via SMTP. To: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("❌ Failed to send invitation email via SMTP: {}", e.getMessage());
            throw new RuntimeException("Failed to send invitation email: " + e.getMessage());
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
}
