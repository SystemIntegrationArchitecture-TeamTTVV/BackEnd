package edu.iuh.fit.se.commonservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.model.GroupMember;
import edu.iuh.fit.se.commonservice.repository.GroupMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;

    // 🔥 Lấy tất cả member trong group
    public List<GroupMember> getMembers(String groupId) {
        return groupMemberRepository.findByGroupId(groupId);
    }
}