package edu.iuh.fit.se.commonservice.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    public void sendPasswordResetEmail(String toEmail, String resetToken, String userName) {
        if (resendApiKey == null || resendApiKey.isEmpty()) {
            log.warn("⚠️ Resend API key not configured. Email not sent.");
            log.info("Reset link (for testing): {}/auth/reset-password?token={}", baseUrl, resetToken);
            return;
        }

        try {
            Resend resend = new Resend(resendApiKey);

            String resetLink = baseUrl + "/auth/reset-password?token=" + resetToken;
            
            String htmlContent = buildPasswordResetEmailHtml(userName, resetLink);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail)
                    .subject("Reset Your Password - TTVV Social Network")
                    .html(htmlContent)
                    .build();

            CreateEmailResponse data = resend.emails().send(params);
            log.info("✅ Password reset email sent successfully. Email ID: {}", data.getId());

        } catch (ResendException e) {
            log.error("❌ Failed to send password reset email: {}", e.getMessage());
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
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: %%23333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, %%23667eea 0%%, %%23764ba2 100%%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                    .content { background: %%23ffffff; padding: 40px 30px; border: 1px solid %%23e0e0e0; }
                    .button { display: inline-block; padding: 14px 40px; background: %%23667eea; color: white; text-decoration: none; border-radius: 8px; font-weight: 600; margin: 20px 0; }
                    .button:hover { background: %%235568d3; }
                    .footer { text-align: center; padding: 20px; color: %%23666; font-size: 14px; }
                    .warning { background: %%23fff3cd; border-left: 4px solid %%23ffc107; padding: 15px; margin: 20px 0; border-radius: 4px; }
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
                        <p style="color: %%23666; font-size: 14px; margin-top: 30px;">
                            Or copy and paste this link into your browser:<br>
                            <a href="%s" style="color: %%23667eea; word-break: break-all;">%s</a>
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2026 TTVV Social Network. All rights reserved.</p>
                        <p style="font-size: 12px; color: %%23999;">This is an automated email. Please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(userName, resetLink, resetLink, resetLink);
    }
}
