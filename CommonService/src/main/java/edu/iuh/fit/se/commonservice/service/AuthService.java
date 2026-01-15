package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.AuthRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AuthResponseDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Role;
import edu.iuh.fit.se.commonservice.model.User;
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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponseDTO login(AuthRequestDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String roleName = user.getRole() != null ? user.getRole().getName() : "USER";
            String token = jwtUtil.generateToken(user.getUsername(), roleName, user.getId());

            return new AuthResponseDTO(
                    token,
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
        user.setActive(true);
        user.setVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        String roleName = saved.getRole() != null ? saved.getRole().getName() : "USER";
        String token = jwtUtil.generateToken(saved.getUsername(), roleName, saved.getId());

        return new AuthResponseDTO(
                token,
                saved.getUsername(),
                roleName,
                saved.getId(),
                saved.getFullName(),
                saved.getAvatar()
        );
    }
}

