package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.GroupDTO;
import edu.iuh.fit.se.commonservice.model.Group;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.GroupRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public List<GroupDTO> getAllGroups() {
        return groupRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public GroupDTO getGroupById(String id) {
        return groupRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + id));
    }

    public List<GroupDTO> searchGroups(String name) {
        return groupRepository.findByNameContainingIgnoreCase(name).stream()
                .filter(Group::isActive)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<GroupDTO> getGroupsByAdminId(String adminId) {
        return groupRepository.findByAdminIdAndIsActiveTrueOrderByCreatedAtDesc(adminId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public GroupDTO createGroup(GroupDTO groupDTO) {
        Group group = toEntity(groupDTO);
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        group.setActive(true);
        Group saved = groupRepository.save(group);
        return toDTO(saved);
    }

    public GroupDTO updateGroup(String id, GroupDTO groupDTO) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + id));
        
        group.setName(groupDTO.getName());
        group.setDescription(groupDTO.getDescription());
        group.setCoverPhoto(groupDTO.getCoverPhoto());
        group.setAvatar(groupDTO.getAvatar());
        group.setUpdatedAt(LocalDateTime.now());
        
        Group updated = groupRepository.save(group);
        return toDTO(updated);
    }

    public void deleteGroup(String id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + id));
        group.setActive(false);
        groupRepository.save(group);
    }

    private GroupDTO toDTO(Group group) {
        GroupDTO dto = new GroupDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setDescription(group.getDescription());
        dto.setCoverPhoto(group.getCoverPhoto());
        dto.setAvatar(group.getAvatar());
        if (group.getAdmin() != null) {
            dto.setAdminId(group.getAdmin().getId());
            dto.setAdminName(group.getAdmin().getFullName());
        }
        dto.setPrivacy(group.getPrivacy());
        dto.setVisibility(group.getVisibility());
        dto.setMemberCount(group.getMemberCount());
        dto.setPostCount(group.getPostCount());
        dto.setTags(group.getTags());
        dto.setCategory(group.getCategory());
        dto.setCreatedAt(group.getCreatedAt());
        dto.setUpdatedAt(group.getUpdatedAt());
        dto.setActive(group.isActive());
        return dto;
    }

    private Group toEntity(GroupDTO dto) {
        Group group = new Group();
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        group.setCoverPhoto(dto.getCoverPhoto());
        group.setAvatar(dto.getAvatar());
        if (dto.getAdminId() != null) {
            User admin = userRepository.findById(dto.getAdminId())
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            group.setAdmin(admin);
        }
        group.setPrivacy(dto.getPrivacy() != null ? dto.getPrivacy() : "PUBLIC");
        group.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "VISIBLE");
        group.setTags(dto.getTags());
        group.setCategory(dto.getCategory());
        return group;
    }
}

