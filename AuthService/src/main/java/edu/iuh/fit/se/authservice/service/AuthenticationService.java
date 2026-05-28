package edu.iuh.fit.se.authservice.service;

import edu.iuh.fit.se.authservice.dto.AuthRequestDTO;
import edu.iuh.fit.se.authservice.dto.AuthResponseDTO;
import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.dto.VerifyOtpResponseDTO;
import edu.iuh.fit.se.authservice.entity.OtpEntity;
import edu.iuh.fit.se.authservice.entity.PasswordResetTokenEntity;
import edu.iuh.fit.se.authservice.entity.RoleEntity;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.event.UserRegisteredEvent;
import edu.iuh.fit.se.authservice.repository.OtpRepository;
import edu.iuh.fit.se.authservice.repository.PasswordResetTokenRepository;
import edu.iuh.fit.se.authservice.repository.RoleRepository;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import edu.iuh.fit.se.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final TokenStoreService tokenStoreService;
    private final DeviceSessionService deviceSessionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    public AuthResponseDTO login(AuthRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserEntity user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), roleName, user.getId());
            String refreshToken = UUID.randomUUID().toString();

            tokenStoreService.storeTokens(accessToken, refreshToken, user.getId());
                deviceSessionService.onLogin(
                    user.getId(),
                    user.getUsername(),
                    refreshToken,
                    currentUserAgent(),
                    currentIpAddress()
                );

            return new AuthResponseDTO(
                    accessToken,
                    refreshToken,
                    user.getUsername(),
                    roleName,
                    user.getId(),
                    user.getFullName(),
                    user.getAvatar()
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Invalid username or password");
        }
    }

    public AuthResponseDTO register(UserDTO userDTO) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        RoleEntity defaultRole = roleRepository.findByName("USER")
                .or(() -> roleRepository.findByName("ROLE_USER"))
                .orElseThrow(() -> new IllegalStateException("Default role USER/ROLE_USER not seeded"));

        UserEntity user = UserEntity.builder()
                .email(userDTO.getEmail())
                .username(userDTO.getUsername())
                .password(passwordEncoder.encode(userDTO.getPassword() != null ? userDTO.getPassword() : "123456"))
                .firstName(userDTO.getFirstName())
                .lastName(userDTO.getLastName())
                .fullName(userDTO.getFullName())
                .avatar(userDTO.getAvatar())
                .coverPhoto(userDTO.getCoverPhoto())
                .bio(userDTO.getBio())
                .city(userDTO.getCity())
                .country(userDTO.getCountry())
                .gender(userDTO.getGender())
                .interests(userDTO.getInterests() != null ? new java.util.ArrayList<>(userDTO.getInterests()) : new java.util.ArrayList<>())
                .role(defaultRole)
                .active(true)
                .verified(false)
                .build();

        UserEntity saved = userRepository.save(user);

        if (kafkaEnabled) {
            kafkaTemplate.send("ttvv.user.registered", saved.getId(), new UserRegisteredEvent(
                    saved.getId(),
                    saved.getUsername(),
                    Instant.now()
            ));
        }

        String roleName = saved.getRole() != null ? saved.getRole().getName() : "USER";
        String accessToken = jwtUtil.generateAccessToken(saved.getUsername(), roleName, saved.getId());
        String refreshToken = UUID.randomUUID().toString();

        tokenStoreService.storeTokens(accessToken, refreshToken, saved.getId());
        deviceSessionService.onRegister(
            saved.getId(),
            saved.getUsername(),
            refreshToken,
            currentUserAgent(),
            currentIpAddress()
        );

        return new AuthResponseDTO(
                accessToken,
                refreshToken,
                saved.getUsername(),
                roleName,
                saved.getId(),
                saved.getFullName(),
                saved.getAvatar()
        );
    }

    @Transactional
    public void requestPasswordReset(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with this email not found"));

        // Xóa OTP cũ, tạo OTP 6 chữ số mới
        otpRepository.deleteByEmail(email);

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpEntity otpEntity = OtpEntity.builder()
                .email(email)
                .code(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
        otpRepository.save(otpEntity);

        emailService.sendOtpEmail(
                email,
                otp,
                user.getFullName() != null ? user.getFullName() : user.getUsername()
        );
    }

    @Transactional
    public VerifyOtpResponseDTO verifyOtp(String email, String otp) {
        OtpEntity otpEntity = otpRepository.findByEmailAndCode(email, otp)
                .orElseThrow(() -> new RuntimeException("Mã xác minh không hợp lệ"));

        if (Boolean.TRUE.equals(otpEntity.getUsed())) {
            throw new RuntimeException("Mã xác minh đã được sử dụng");
        }

        if (otpEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Mã xác minh đã hết hạn. Vui lòng yêu cầu mã mới");
        }

        // Đánh dấu OTP đã dùng
        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);

        // Tìm user và tạo reset token
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String resetToken = UUID.randomUUID().toString();
        PasswordResetTokenEntity resetTokenEntity = PasswordResetTokenEntity.builder()
                .userId(user.getId())
                .token(resetToken)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
        passwordResetTokenRepository.save(resetTokenEntity);

        return new VerifyOtpResponseDTO(resetToken, "Xác minh thành công");
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new RuntimeException("This reset link has already been used");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This reset link has expired. Please request a new one");
        }

        UserEntity user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    public AuthResponseDTO refreshAccessToken(String refreshToken) {
        String userId = tokenStoreService.getUserIdForRefreshToken(refreshToken);
        if (userId == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";

        String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), roleName, user.getId());
        String newRefreshToken = UUID.randomUUID().toString();

        tokenStoreService.storeTokens(newAccessToken, newRefreshToken, user.getId());
        tokenStoreService.deleteRefreshToken(refreshToken);
        deviceSessionService.onRefresh(
            refreshToken,
            newRefreshToken,
            currentUserAgent(),
            currentIpAddress()
        );

        return new AuthResponseDTO(
                newAccessToken,
                newRefreshToken,
                user.getUsername(),
                roleName,
                user.getId(),
                user.getFullName(),
                user.getAvatar()
        );
    }

    public void logout(String refreshToken, String accessToken) {
        deviceSessionService.onLogout(refreshToken);
        tokenStoreService.deleteRefreshToken(refreshToken);
        // Blacklist the Access Token's JTI for its remaining TTL.
        // This ensures the token is rejected at the Gateway even though
        // Access Tokens are Stateless (not stored in Redis).
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String jti = jwtUtil.extractJti(accessToken);
                long remainingTtl = jwtUtil.extractRemainingTtlMs(accessToken);
                tokenStoreService.blacklistAccessToken(jti, remainingTtl);
            } catch (Exception e) {
                // Token already expired or invalid — no need to blacklist
            }
        }
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[0].trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
