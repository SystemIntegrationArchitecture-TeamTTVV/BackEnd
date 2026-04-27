package edu.iuh.fit.se.authservice.service;

import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.entity.RoleEntity;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.mapper.UserMapper;
import edu.iuh.fit.se.authservice.repository.RoleRepository;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toDto).collect(Collectors.toList());
    }

    @Cacheable(value = "users", key = "#id")
    @Transactional(readOnly = true)
    public UserDTO getUserById(String id) {
        return userRepository.findById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Cacheable(value = "users", key = "'username:' + #username")
    @Transactional(readOnly = true)
    public UserDTO getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String query) {
        List<UserEntity> byName = userRepository.findByFullNameContainingIgnoreCase(query);
        List<UserEntity> byUsername = userRepository.findByUsernameContainingIgnoreCase(query);
        Set<String> seenIds = new HashSet<>();
        return Stream.concat(byName.stream(), byUsername.stream())
                .filter(u -> seenIds.add(u.getId()))
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    public Map<String, Object> metricsSummary() {
        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        long total = userRepository.count();
        long active = userRepository.countByActiveTrue();
        long newMonth = userRepository.countByCreatedAtAfter(monthStart);
        return Map.of(
                "totalUsers", total,
                "activeUsers", active,
                "newUsersThisMonth", newMonth
        );
    }

    @Transactional(readOnly = true)
    public List<UserDTO> batchLookup(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> distinct = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(distinct).stream().map(userMapper::toDto).toList();
    }

    public UserDTO createUserWithPassword(UserDTO userDTO, String password) {
        UserEntity user = toNewEntity(userDTO);
        user.setPassword(passwordEncoder.encode(password != null ? password : "123456"));
        UserEntity saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserDTO updateUser(String id, UserDTO userDTO) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (userDTO.getFirstName() != null) {
            user.setFirstName(userDTO.getFirstName());
        }
        if (userDTO.getLastName() != null) {
            user.setLastName(userDTO.getLastName());
        }
        if (userDTO.getFullName() != null) {
            user.setFullName(userDTO.getFullName());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        if (userDTO.getCoverPhoto() != null) {
            user.setCoverPhoto(userDTO.getCoverPhoto());
        }
        if (userDTO.getBio() != null) {
            user.setBio(userDTO.getBio());
        }
        if (userDTO.getCity() != null) {
            user.setCity(userDTO.getCity());
        }
        if (userDTO.getCountry() != null) {
            user.setCountry(userDTO.getCountry());
        }
        if (userDTO.getPhoneNumber() != null) {
            user.setPhoneNumber(userDTO.getPhoneNumber());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }
        if (userDTO.getDateOfBirth() != null) {
            user.setDateOfBirth(userDTO.getDateOfBirth());
        }
        if (userDTO.getGender() != null) {
            user.setGender(userDTO.getGender());
        }
        if (userDTO.getWorkPlace() != null) {
            user.setWorkPlace(userDTO.getWorkPlace());
        }
        if (userDTO.getEducation() != null) {
            user.setEducation(userDTO.getEducation());
        }
        if (userDTO.getRelationshipStatus() != null) {
            user.setRelationshipStatus(userDTO.getRelationshipStatus());
        }
        if (userDTO.getInterests() != null) {
            user.setInterests(new ArrayList<>(userDTO.getInterests()));
        }
        if (userDTO.getProfileVisibility() != null) {
            user.setProfileVisibility(userDTO.getProfileVisibility());
        }
        if (userDTO.getPostVisibility() != null) {
            user.setPostVisibility(userDTO.getPostVisibility());
        }
        if (userDTO.getShowEmail() != null) {
            user.setShowEmail(userDTO.getShowEmail());
        }
        if (userDTO.getShowPhone() != null) {
            user.setShowPhone(userDTO.getShowPhone());
        }
        if (userDTO.getAllowMessageFrom() != null) {
            user.setAllowMessageFrom(userDTO.getAllowMessageFrom());
        }
        if (userDTO.getAllowCallFrom() != null) {
            user.setAllowCallFrom(userDTO.getAllowCallFrom());
        }
        if (userDTO.getAllowGroupInviteFrom() != null) {
            user.setAllowGroupInviteFrom(userDTO.getAllowGroupInviteFrom());
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toDto(userRepository.save(user));
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public UserDTO updateUserRole(String id, String roleName) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        RoleEntity role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public UserDTO updateUserStatus(String id, String status) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setActive("ACTIVE".equalsIgnoreCase(status));
        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toDto(userRepository.save(user));
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void deactivateUser(String id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setActive(false);
        userRepository.save(user);
    }

    private UserEntity toNewEntity(UserDTO dto) {
        RoleEntity role = roleRepository.findByName(dto.getRole() != null ? dto.getRole() : "USER")
                .orElseGet(() -> roleRepository.findByName("USER").orElseThrow());

        return UserEntity.builder()
                .email(dto.getEmail())
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .fullName(dto.getFullName())
                .avatar(dto.getAvatar())
                .coverPhoto(dto.getCoverPhoto())
                .bio(dto.getBio())
                .city(dto.getCity())
                .country(dto.getCountry())
                .gender(dto.getGender())
                .interests(dto.getInterests() != null ? new ArrayList<>(dto.getInterests()) : new ArrayList<>())
                .role(role)
                .active(true)
                .verified(false)
                .phoneNumber(dto.getPhoneNumber())
                .dateOfBirth(dto.getDateOfBirth())
                .workPlace(dto.getWorkPlace())
                .education(dto.getEducation())
                .relationshipStatus(dto.getRelationshipStatus())
                .profileVisibility(dto.getProfileVisibility() != null ? dto.getProfileVisibility() : "PUBLIC")
                .postVisibility(dto.getPostVisibility() != null ? dto.getPostVisibility() : "PUBLIC")
                .showEmail(dto.getShowEmail() != null ? dto.getShowEmail() : false)
                .showPhone(dto.getShowPhone() != null ? dto.getShowPhone() : false)
                .allowMessageFrom(dto.getAllowMessageFrom() != null ? dto.getAllowMessageFrom() : "EVERYONE")
                .allowCallFrom(dto.getAllowCallFrom() != null ? dto.getAllowCallFrom() : "EVERYONE")
                .allowGroupInviteFrom(dto.getAllowGroupInviteFrom() != null ? dto.getAllowGroupInviteFrom() : "EVERYONE")
                .build();
    }
}
