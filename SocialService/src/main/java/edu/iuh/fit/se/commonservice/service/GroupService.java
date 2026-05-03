package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.FriendInviteDTO;
import edu.iuh.fit.se.commonservice.dto.GroupDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Group;
import edu.iuh.fit.se.commonservice.model.GroupMember;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.GroupMemberRepository;
import edu.iuh.fit.se.commonservice.repository.GroupRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final AuthServiceClient authServiceClient;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendRepository friendRepository;
    private final NotificationService notificationService;
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
        adminMember.setUserId(groupDTO.getAdminId());

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
        if (groupDTO.getLinkedConversationId() != null) {
            group.setLinkedConversationId(groupDTO.getLinkedConversationId());
        }
        if (groupDTO.getJoinQuestions() != null) {
            group.setJoinQuestions(groupDTO.getJoinQuestions());
        }
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
        if (group.getAdminId() != null) {
            try {
                UserDTO admin = authServiceClient.getUserById(group.getAdminId());
                dto.setAdminId(admin.getId());
                dto.setAdminName(admin.getFullName() != null ? admin.getFullName() : admin.getUsername());
            } catch (Exception e) {
                dto.setAdminId(group.getAdminId());
                dto.setAdminName("Unknown");
            }
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
        dto.setLinkedConversationId(group.getLinkedConversationId());
        dto.setJoinQuestions(group.getJoinQuestions());
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
            group.setAdminId(dto.getAdminId());
        }
        group.setPrivacy(dto.getPrivacy() != null ? dto.getPrivacy() : "PUBLIC");
        group.setVisibility(dto.getVisibility() != null ? dto.getVisibility() : "VISIBLE");
        group.setTags(dto.getTags());
        group.setCategory(dto.getCategory());
        group.setLinkedConversationId(dto.getLinkedConversationId());
        group.setJoinQuestions(dto.getJoinQuestions());
        return group;
    }
    public void addMembers(String groupId, List<String> userIds) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        for (String userId : userIds) {

            try {
                authServiceClient.getUserById(userId);
            } catch (Exception e) {
                throw new RuntimeException("User not found");
            }

            Optional<GroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(groupId, userId);

            if (existing.isPresent()) {
                GroupMember member = existing.get();

                if ("ACTIVE".equals(member.getStatus()) || "INVITED".equals(member.getStatus())) {
                    continue;
                }

                member.setRole("MEMBER");
                member.setStatus("INVITED");
                member.setUpdatedAt(LocalDateTime.now());
                groupMemberRepository.save(member);
                createGroupInviteNotification(group, userId);
                continue;
            }

            GroupMember member = new GroupMember();
            member.setGroup(group);
            member.setGroupId(groupId);
            member.setUserId(userId);

            member.setRole("MEMBER");
            member.setStatus("INVITED");

            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);
            createGroupInviteNotification(group, userId);
        }

        // 🔥 tính lại memberCount từ database
        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        group.setMemberCount((int) count);
        groupRepository.save(group);
    }

    private void createGroupInviteNotification(Group group, String recipientId) {
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(recipientId);

        if (group.getAdminId() != null) {
            try {
                UserDTO admin = authServiceClient.getUserById(group.getAdminId());
                notification.setActorId(admin.getId());
                notification.setActorName(admin.getFullName() != null ? admin.getFullName() : admin.getUsername());
                notification.setActorAvatar(admin.getAvatar());
            } catch (Exception e) {
                // ignore
            }
        }

        notification.setType("GROUP_INVITE");
        notification.setTitle("Lời mời vào nhóm");
        notification.setContent("đã mời bạn vào nhóm " + group.getName());
        notification.setRelatedId(group.getId());
        notification.setRelatedType("GROUP");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationService.createNotification(notification);
    }
    public long getMemberCount(String groupId) {
        return groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");
    }
    public List<UserDTO> getGroupMembers(String groupId) {
        List<String> userIds = groupMemberRepository
                .findByGroupIdAndStatus(groupId, "ACTIVE")
                .stream()
                .map(GroupMember::getUserId)
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return authServiceClient.batchLookup(userIds);
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
    public void joinGroup(String groupId, String userId, java.util.List<String> answers) {

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        try {
            authServiceClient.getUserById(userId);
        } catch (Exception e) {
            throw new RuntimeException("User not found");
        }

        Optional<GroupMember> existing =
                groupMemberRepository.findByGroupIdAndUserId(groupId, userId);

        String newStatus = "ACTIVE"; // default
        if ("PRIVATE".equals(group.getPrivacy())) {
            newStatus = "PENDING";
        }

        if (existing.isPresent()) {
            GroupMember member = existing.get();

            if ("INVITED".equals(member.getStatus())) {
                newStatus = "ACTIVE";
            }

            // nếu đang ACTIVE hoặc PENDING thì thôi
            if (newStatus.equals(member.getStatus())) {
                return;
            }

            member.setStatus(newStatus);
            if (answers != null && !answers.isEmpty()) {
                member.setJoinAnswers(answers);
            }
            
            if ("ACTIVE".equals(newStatus)) {
                member.setJoinedAt(LocalDateTime.now());
            }
            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);

        } else {

            GroupMember member = new GroupMember();

            member.setGroup(group);
            member.setGroupId(groupId);

            member.setUserId(userId);

            member.setRole("MEMBER");
            member.setStatus(newStatus);
            if (answers != null && !answers.isEmpty()) {
                member.setJoinAnswers(answers);
            }

            member.setJoinedAt(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());

            groupMemberRepository.save(member);
        }

        // Chỉ đếm những ACTIVE thôi
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
                .filter(Group::isActive)
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
            .findByGroupId(groupId)
                .stream()
            .filter(member -> !"REMOVED".equals(member.getStatus()) && !"REJECTED".equals(member.getStatus()))
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());

        // 3. Lọc bạn bè chưa trong group
        return authServiceClient.batchLookup(
                friendIds.stream()
                        .filter(id -> !memberIds.contains(id))
                        .toList()
        ).stream().map(user -> {
            FriendInviteDTO dto = new FriendInviteDTO();
            dto.setId(user.getId());
            dto.setFullName(user.getFullName() != null ? user.getFullName() : user.getUsername());
            dto.setAvatar(user.getAvatar());
            return dto;
        }).toList();
    }

    public String getUserStatus(String groupId, String userId) {
        return groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::getStatus)
                .orElse(null);
    }
    public List<GroupMember> getPendingMembers(String groupId) {
        return groupMemberRepository
                .findByGroupIdAndStatus(groupId, "PENDING");
    }
    public GroupDTO toggleGroupPrivacy(String groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found with id: " + groupId));

        // Kiểm tra trạng thái hiện tại
        String currentPrivacy = group.getPrivacy();

        if ("PUBLIC".equals(currentPrivacy)) {
            // từ PUBLIC -> PRIVATE
            group.setPrivacy("PRIVATE");
        } else if ("PRIVATE".equals(currentPrivacy)) {
            // từ PRIVATE -> PUBLIC
            group.setPrivacy("PUBLIC");

            // Chuyển tất cả PENDING member sang ACTIVE
            List<GroupMember> pendingMembers = groupMemberRepository.findByGroupIdAndStatus(groupId, "PENDING");
            for (GroupMember member : pendingMembers) {
                member.setStatus("ACTIVE");
                member.setUpdatedAt(LocalDateTime.now());
            }
            groupMemberRepository.saveAll(pendingMembers);
        } else {
            throw new RuntimeException("Cannot toggle privacy for group with privacy: " + currentPrivacy);
        }

        group.setUpdatedAt(LocalDateTime.now());

        // cập nhật lại memberCount chỉ tính ACTIVE
        long activeCount = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");
        group.setMemberCount((int) activeCount);

        Group updated = groupRepository.save(group);
        return toDTO(updated);
    }
    public void approveMember(String groupId, String userId) {

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (!"PENDING".equals(member.getStatus())) {
            throw new RuntimeException("Member is not in PENDING status");
        }

        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberRepository.save(member);

        // Cập nhật lại memberCount
        long count = groupMemberRepository.countByGroupIdAndStatus(groupId, "ACTIVE");

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        group.setMemberCount((int) count);
        groupRepository.save(group);
    }

    public void rejectMember(String groupId, String userId) {

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (!"PENDING".equals(member.getStatus())) {
            throw new RuntimeException("Member is not in PENDING status");
        }

        member.setStatus("REJECTED");
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberRepository.save(member);
    }

    public void rejectGroupInvite(String groupId, String userId) {

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Invite not found"));

        if (!"INVITED".equals(member.getStatus())) {
            throw new RuntimeException("User is not in INVITED status");
        }

        member.setStatus("REJECTED");
        member.setUpdatedAt(LocalDateTime.now());
        groupMemberRepository.save(member);
    }
}

