package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendInviteDTO;
import edu.iuh.fit.se.commonservice.dto.GroupDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Group;
import edu.iuh.fit.se.commonservice.model.GroupMember;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.GroupMemberRepository;
import edu.iuh.fit.se.commonservice.repository.GroupRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendRepository friendRepository;
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

        // add admin vào group_members
        GroupMember adminMember = new GroupMember();
        adminMember.setGroup(saved);
        adminMember.setGroupId(saved.getId());
        adminMember.setUser(saved.getAdmin());
        adminMember.setUserId(saved.getAdmin().getId());

        adminMember.setRole("ADMIN");
        adminMember.setStatus("ACTIVE");
        adminMember.setJoinedAt(LocalDateTime.now());
        adminMember.setUpdatedAt(LocalDateTime.now());

        groupMemberRepository.save(adminMember);

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
        dto.setMemberCount(
                (int) groupMemberRepository
                        .countByGroupIdAndStatus(group.getId(), "ACTIVE")
        );
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
    public void addMembers(String groupId, List<String> userIds) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        for (String userId : userIds) {

            if (groupMemberRepository.existsByUserIdAndGroupId(userId, groupId)) {
                continue;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            GroupMember member = new GroupMember();
            member.setGroup(group);
            member.setGroupId(groupId);
            member.setUser(user);
            member.setUserId(userId);

            member.setRole("MEMBER");
            member.setStatus("ACTIVE");

            member.setJoinedAt(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);
        }

        // 🔥 tính lại memberCount từ database
        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        group.setMemberCount((int) count);
        groupRepository.save(group);
    }
    public long getMemberCount(String groupId) {
        return groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");
    }
    public List<User> getGroupMembers(String groupId) {

        return groupMemberRepository
                .findByGroupIdAndStatus(groupId, "ACTIVE")
                .stream()
                .map(GroupMember::getUser)
                .collect(Collectors.toList());
    }
    public void removeMember(String groupId, String userId) {

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        member.setStatus("REMOVED");
        member.setUpdatedAt(LocalDateTime.now());

        groupMemberRepository.save(member);

        // cập nhật member count
        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        group.setMemberCount((int) count);

        groupRepository.save(group);
    }
    public boolean isUserMember(String groupId, String userId) {

        return groupMemberRepository
                .existsByGroupIdAndUserIdAndStatus(groupId, userId, "ACTIVE");

    }
    public void joinGroup(String groupId, String userId) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<GroupMember> existing =
                groupMemberRepository.findByGroupIdAndUserId(groupId, userId);

        if (existing.isPresent()) {

            GroupMember member = existing.get();

            // nếu đang ACTIVE thì thôi
            if ("ACTIVE".equals(member.getStatus())) {
                return;
            }

            // nếu REMOVED thì kích hoạt lại
            member.setStatus("ACTIVE");
            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);

        } else {

            GroupMember member = new GroupMember();

            member.setGroup(group);
            member.setGroupId(groupId);

            member.setUser(user);
            member.setUserId(userId);

            member.setRole("MEMBER");
            member.setStatus("ACTIVE");

            member.setJoinedAt(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);
        }

        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        group.setMemberCount((int) count);

        groupRepository.save(group);
    }
    public String getUserRole(String groupId, String userId) {

        return groupMemberRepository
                .findByGroupIdAndUserIdAndStatus(groupId, userId, "ACTIVE")
                .map(GroupMember::getRole)
                .orElse(null);
    }
    public void leaveGroup(String groupId, String userId) {

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // admin không được rời nhóm
        if ("ADMIN".equals(member.getRole())) {
            throw new RuntimeException("Admin cannot leave group");
        }

        member.setStatus("REMOVED");
        member.setUpdatedAt(LocalDateTime.now());

        groupMemberRepository.save(member);

        // cập nhật lại memberCount
        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        group.setMemberCount((int) count);

        groupRepository.save(group);
    }
    public List<GroupDTO> getGroupsByUserId(String userId) {

        return groupMemberRepository
                .findByUserIdAndStatus(userId, "ACTIVE")
                .stream()
                .map(GroupMember::getGroup)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    public List<FriendInviteDTO> getInvitableFriends(String groupId, String userId) {

        // 1. Lấy danh sách bạn bè
        List<String> friendIds = friendRepository.findByUserId(userId)
                .stream()
                .map(Friend::getFriendId)
                .toList();

        // 2. Lấy member ACTIVE của group
        Set<String> memberIds = groupMemberRepository
                .findByGroupIdAndStatus(groupId, "ACTIVE")
                .stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        // 3. Lọc bạn bè chưa trong group
        return userRepository.findAllById(friendIds)
                .stream()
                .filter(user -> !memberIds.contains(user.getId()))
                .map(user -> {
                    FriendInviteDTO dto = new FriendInviteDTO();
                    dto.setId(user.getId());
                    dto.setFullName(user.getFullName());
                    dto.setAvatar(user.getAvatar());
                    return dto;
                })
                .toList();
    }
}

