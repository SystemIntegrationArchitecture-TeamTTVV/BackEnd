package edu.iuh.fit.se.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEXT_LENGTH = 5;

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${recaptcha.enabled:true}")
    private boolean enabled;

    /**
     * Generates a one-time captcha: random text → stored in Redis, image returned as base64.
     */
    public Map<String, String> generateChallenge() {
        String text = randomText();
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, text.toUpperCase(), TOKEN_TTL);
        log.debug("Captcha generated token={}", token);
        return Map.of("token", token, "image", buildImage(text));
    }

    /**
     * Verifies user input against the stored captcha text.
     * Token is deleted after first use (one-time).
     */
    public boolean verify(String token, String userInput) {
        if (!enabled) {
            log.debug("Captcha disabled — skipping");
            return true;
        }
        if (token == null || token.isBlank() || userInput == null || userInput.isBlank()) {
            log.warn("Captcha token or input missing");
            return false;
        }
        String key = KEY_PREFIX + token;
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null) {
            log.warn("Captcha token not found or expired: {}", token);
            return false;
        }
        boolean match = stored.equalsIgnoreCase(userInput.trim());
        if (match) {
            stringRedisTemplate.delete(key);
        } else {
            log.warn("Captcha mismatch: expected={} got={}", stored, userInput);
        }
        return match;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String randomText() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder(TEXT_LENGTH);
        for (int i = 0; i < TEXT_LENGTH; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String buildImage(String text) {
        int width = 150, height = 50;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Random r = new Random();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, width, height);

        // Noise lines
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(r.nextInt(180) + 60, r.nextInt(180) + 60, r.nextInt(180) + 60));
            g.drawLine(r.nextInt(width), r.nextInt(height), r.nextInt(width), r.nextInt(height));
        }

        // Draw each character with slight random rotation
        Font font = new Font("Arial", Font.BOLD, 26);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);
        int charW = width / (text.length() + 1);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int x = charW * i + charW / 2;
            int y = height / 2 + fm.getAscent() / 2 - 6;
            double angle = (r.nextDouble() - 0.5) * 0.55;
            Graphics2D gc = (Graphics2D) g.create();
            gc.setColor(new Color(r.nextInt(80), r.nextInt(80), r.nextInt(160) + 60));
            gc.rotate(angle, x + 8, y - fm.getAscent() / 2);
            gc.drawString(ch, x, y);
            gc.dispose();
        }

        // Noise dots
        for (int i = 0; i < 80; i++) {
            g.setColor(new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256), 120));
            g.fillRect(r.nextInt(width), r.nextInt(height), 2, 2);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
