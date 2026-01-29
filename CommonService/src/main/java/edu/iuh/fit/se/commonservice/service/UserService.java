package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Role;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.RoleRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(String id) {
        return userRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public UserDTO getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    public UserDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    public List<UserDTO> searchUsers(String query) {
        List<User> byName = userRepository.findByFullNameContainingIgnoreCase(query);
        List<User> byUsername = userRepository.findByUsernameContainingIgnoreCase(query);
        
        // Combine and remove duplicates
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        return java.util.stream.Stream.concat(byName.stream(), byUsername.stream())
                .filter(user -> seenIds.add(user.getId()))
                .map(this::toDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO createUser(UserDTO userDTO) {
        // For admin creating users, use default password
        return createUserWithPassword(userDTO, "123456");
    }

    public UserDTO updateUser(String id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        // Basic info
        if (userDTO.getFirstName() != null) {
            user.setFirstName(userDTO.getFirstName());
        }
        if (userDTO.getLastName() != null) {
            user.setLastName(userDTO.getLastName());
        }
        if (userDTO.getFullName() != null) {
            user.setFullName(userDTO.getFullName());
        }

        // Images
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        if (userDTO.getCoverPhoto() != null) {
            user.setCoverPhoto(userDTO.getCoverPhoto());
        }

        // Profile details
        if (userDTO.getBio() != null) {
            user.setBio(userDTO.getBio());
        }
        if (userDTO.getCity() != null) {
            user.setCity(userDTO.getCity());
        }
        if (userDTO.getCountry() != null) {
            user.setCountry(userDTO.getCountry());
        }

        // Contact info
        if (userDTO.getPhoneNumber() != null) {
            user.setPhoneNumber(userDTO.getPhoneNumber());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }

        // Personal info
        if (userDTO.getDateOfBirth() != null) {
            user.setDateOfBirth(userDTO.getDateOfBirth());
        }
        if (userDTO.getGender() != null) {
            user.setGender(userDTO.getGender());
        }

        // Work & Education
        if (userDTO.getWorkPlace() != null) {
            user.setWorkPlace(userDTO.getWorkPlace());
        }
        if (userDTO.getEducation() != null) {
            user.setEducation(userDTO.getEducation());
        }
        if (userDTO.getRelationshipStatus() != null) {
            user.setRelationshipStatus(userDTO.getRelationshipStatus());
        }

        // Interests
        if (userDTO.getInterests() != null) {
            user.setInterests(userDTO.getInterests());
        }

        // Privacy settings
        if (userDTO.getProfileVisibility() != null) {
            user.setProfileVisibility(userDTO.getProfileVisibility());
        }
        if (userDTO.getPostVisibility() != null) {
            user.setPostVisibility(userDTO.getPostVisibility());
        }

        // Boolean fields - cần kiểm tra cẩn thận vì có thể là false
        user.setShowEmail(userDTO.getShowEmail());
        user.setShowPhone(userDTO.getShowPhone());

        user.setUpdatedAt(LocalDateTime.now());

        User updated = userRepository.save(user);
        return toDTO(updated);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setFullName(user.getFullName());
        dto.setAvatar(user.getAvatar());
        dto.setCoverPhoto(user.getCoverPhoto());
        dto.setBio(user.getBio());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setDateOfBirth(user.getDateOfBirth());
        dto.setGender(user.getGender());
        dto.setCity(user.getCity());
        dto.setCountry(user.getCountry());
        dto.setWorkPlace(user.getWorkPlace());
        dto.setEducation(user.getEducation());
        dto.setRelationshipStatus(user.getRelationshipStatus());
        dto.setIsActive(user.getIsActive());
        dto.setIsVerified(user.getIsVerified());
        dto.setRole(user.getRole() != null ? user.getRole().getName() : "USER");
        dto.setInterests(user.getInterests());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setProfileVisibility(user.getProfileVisibility());
        dto.setPostVisibility(user.getPostVisibility());
        dto.setShowEmail(user.getShowEmail());
        dto.setShowPhone(user.getShowPhone());
        return dto;
    }

    private User toEntity(UserDTO dto) {
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        // Password should be set separately, not from DTO
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setFullName(dto.getFullName());
        user.setAvatar(dto.getAvatar());
        user.setCoverPhoto(dto.getCoverPhoto());
        user.setBio(dto.getBio());
        user.setCity(dto.getCity());
        user.setCountry(dto.getCountry());
        user.setGender(dto.getGender());
        user.setInterests(dto.getInterests());
        
        // Set role
        String roleName = dto.getRole() != null ? dto.getRole() : "USER";
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    // Create default USER role if not exists
                    Role newRole = new Role();
                    newRole.setName("USER");
                    newRole.setDescription("Regular user");
                    newRole.setActive(true);
                    newRole.setCreatedAt(LocalDateTime.now());
                    newRole.setUpdatedAt(LocalDateTime.now());
                    return roleRepository.save(newRole);
                });
        user.setRole(role);
        user.setRoleId(role.getId());
        
        return user;
    }
    
    public UserDTO createUserWithPassword(UserDTO userDTO, String password) {
        User user = toEntity(userDTO);
        user.setPassword(passwordEncoder.encode(password != null ? password : "123456"));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        return toDTO(saved);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO updateUserRole(String id, String roleName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        
        user.setRole(role);
        user.setRoleId(role.getId());
        user.setUpdatedAt(LocalDateTime.now());
        User updated = userRepository.save(user);
        return toDTO(updated);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public UserDTO updateUserStatus(String id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        boolean isActive = "ACTIVE".equalsIgnoreCase(status);
        user.setIsActive(isActive);
        user.setUpdatedAt(LocalDateTime.now());
        User updated = userRepository.save(user);
        return toDTO(updated);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setIsActive(false);
        userRepository.save(user);
    }
}

