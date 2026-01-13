package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

    public UserDTO createUser(UserDTO userDTO) {
        User user = toEntity(userDTO);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        return toDTO(saved);
    }

    public UserDTO updateUser(String id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setFullName(userDTO.getFullName());
        user.setAvatar(userDTO.getAvatar());
        user.setCoverPhoto(userDTO.getCoverPhoto());
        user.setBio(userDTO.getBio());
        user.setCity(userDTO.getCity());
        user.setCountry(userDTO.getCountry());
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
        dto.setActive(user.isActive());
        dto.setVerified(user.isVerified());
        dto.setRole(user.getRole());
        dto.setInterests(user.getInterests());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setProfileVisibility(user.getProfileVisibility());
        dto.setPostVisibility(user.getPostVisibility());
        dto.setShowEmail(user.isShowEmail());
        dto.setShowPhone(user.isShowPhone());
        return dto;
    }

    private User toEntity(UserDTO dto) {
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPassword("{noop}123456"); // Default password, should be set separately
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
        return user;
    }
}

