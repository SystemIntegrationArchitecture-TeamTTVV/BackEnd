package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.AuthRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AuthResponseDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.PasswordResetToken;
import edu.iuh.fit.se.commonservice.model.Role;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.PasswordResetTokenRepository;
import edu.iuh.fit.se.commonservice.repository.RoleRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import edu.iuh.fit.se.commonservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final TokenStoreService tokenStoreService;

    public AuthResponseDTO login(AuthRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), roleName, user.getId());
            // We keep refresh token as a separate opaque value stored in Redis
            String refreshToken = UUID.randomUUID().toString();

            tokenStoreService.storeTokens(accessToken, refreshToken, user.getId());

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

        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword() != null ? userDTO.getPassword() : "123456"));
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setFullName(userDTO.getFullName());
        user.setAvatar(userDTO.getAvatar());
        user.setCoverPhoto(userDTO.getCoverPhoto());
        user.setBio(userDTO.getBio());
        user.setCity(userDTO.getCity());
        user.setCountry(userDTO.getCountry());
        user.setGender(userDTO.getGender());
        user.setInterests(userDTO.getInterests());
        
        // Set default USER role
        Role defaultRole = roleRepository.findByName("USER")
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName("USER");
                    newRole.setDescription("Regular user");
                    newRole.setActive(true);
                    newRole.setCreatedAt(LocalDateTime.now());
                    newRole.setUpdatedAt(LocalDateTime.now());
                    return roleRepository.save(newRole);
                });
        user.setRole(defaultRole);
        user.setRoleId(defaultRole.getId());
        user.setIsActive(true);
        user.setIsVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        String roleName = saved.getRole() != null ? saved.getRole().getName() : "USER";
        String accessToken = jwtUtil.generateAccessToken(saved.getUsername(), roleName, saved.getId());
        String refreshToken = UUID.randomUUID().toString();

        tokenStoreService.storeTokens(accessToken, refreshToken, saved.getId());

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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with this email not found"));

        // Delete any existing tokens for this user
        passwordResetTokenRepository.deleteByUserId(user.getId());

        // Generate new token
        String token = UUID.randomUUID().toString();
        
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setToken(token);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
        resetToken.setUsed(false);
        resetToken.setCreatedAt(LocalDateTime.now());
        
        passwordResetTokenRepository.save(resetToken);

        // Send email
        emailService.sendPasswordResetEmail(
                user.getEmail(),
                token,
                user.getFullName() != null ? user.getFullName() : user.getUsername()
        );
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.getUsed()) {
            throw new RuntimeException("This reset link has already been used");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This reset link has expired. Please request a new one");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    public AuthResponseDTO refreshAccessToken(String refreshToken) {
        String userId = tokenStoreService.getUserIdForRefreshToken(refreshToken);
        if (userId == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String roleName = user.getRole() != null ? user.getRole().getName() : "USER";

        String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), roleName, user.getId());
        String newRefreshToken = UUID.randomUUID().toString();

        tokenStoreService.storeTokens(newAccessToken, newRefreshToken, user.getId());
        tokenStoreService.deleteRefreshToken(refreshToken);

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

    public void logout(String refreshToken) {
        tokenStoreService.deleteRefreshToken(refreshToken);
    }
}
