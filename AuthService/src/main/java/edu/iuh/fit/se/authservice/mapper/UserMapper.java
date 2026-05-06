package edu.iuh.fit.se.authservice.mapper;

import edu.iuh.fit.se.authservice.dto.UserDTO;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class UserMapper {

    public UserDTO toDto(UserEntity user) {
        if (user == null) {
            return null;
        }
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
        dto.setIsActive(user.getActive());
        dto.setIsVerified(user.getVerified());
        dto.setRole(user.getRole() != null ? user.getRole().getName() : "USER");
        dto.setInterests(user.getInterests() != null ? new ArrayList<>(user.getInterests()) : new ArrayList<>());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setProfileVisibility(user.getProfileVisibility());
        dto.setPostVisibility(user.getPostVisibility());
        dto.setShowEmail(user.getShowEmail());
        dto.setShowPhone(user.getShowPhone());
        dto.setAllowMessageFrom(user.getAllowMessageFrom());
        dto.setAllowCallFrom(user.getAllowCallFrom());
        dto.setAllowGroupInviteFrom(user.getAllowGroupInviteFrom());
        dto.setStatusText(user.getStatusText());
        dto.setStatusEmoji(user.getStatusEmoji());
        return dto;
    }
}
