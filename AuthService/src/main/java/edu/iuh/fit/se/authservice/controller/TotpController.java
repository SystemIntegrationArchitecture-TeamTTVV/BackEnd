package edu.iuh.fit.se.authservice.controller;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import edu.iuh.fit.se.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/totp")
@RequiredArgsConstructor
public class TotpController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.features.two-factor.enabled:false}")
    private boolean featureEnabled;

    private static final String ISSUER = "TTVV Social";

    /** POST /api/users/me/totp/setup  → returns {secret, qrCodeDataUrl} */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, String>> setup(
            @RequestHeader("Authorization") String authHeader
    ) throws QrGenerationException {
        checkFeature();
        UserEntity user = resolveUser(authHeader);

        String secret = new DefaultSecretGenerator().generate();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false); // not confirmed yet
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail() != null ? user.getEmail() : user.getUsername())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        QrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData = generator.generate(qrData);
        String qrBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);

        return ResponseEntity.ok(Map.of("secret", secret, "qrCodeDataUrl", qrBase64));
    }

    /** POST /api/users/me/totp/verify  body: {code:"123456"}  → enables 2FA */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body
    ) {
        checkFeature();
        UserEntity user = resolveUser(authHeader);
        if (user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA setup not initiated");
        }

        String code = body.getOrDefault("code", "").strip();
        if (code.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code required");

        CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        boolean valid = verifier.isValidCode(user.getTotpSecret(), code);
        if (!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code");

        user.setTotpEnabled(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("totpEnabled", true));
    }

    /** DELETE /api/users/me/totp  body: {code:"123456"}  → disables 2FA */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> disable(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body
    ) {
        checkFeature();
        UserEntity user = resolveUser(authHeader);
        if (!user.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2FA is not enabled");
        }

        String code = body.getOrDefault("code", "").strip();
        CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        if (!verifier.isValidCode(user.getTotpSecret(), code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code");
        }

        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("totpEnabled", false));
    }

    /** GET /api/users/me/totp/status */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader("Authorization") String authHeader
    ) {
        checkFeature();
        UserEntity user = resolveUser(authHeader);
        return ResponseEntity.ok(Map.of("totpEnabled", user.isTotpEnabled()));
    }

    // ── helpers ──────────────────────────────────────────────────
    private void checkFeature() {
        if (!featureEnabled) throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "2FA feature is disabled");
    }

    private UserEntity resolveUser(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String userId = jwtUtil.extractUserId(token);
        if (userId == null || userId.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
