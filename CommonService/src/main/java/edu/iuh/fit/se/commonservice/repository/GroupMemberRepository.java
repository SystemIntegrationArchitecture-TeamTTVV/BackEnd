package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.GroupMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends MongoRepository<GroupMember, String> {

    List<GroupMember> findByGroupId(String groupId);

    boolean existsByUserIdAndGroupId(String userId, String groupId);
    long countByGroupIdAndStatus(String groupId, String status);
    List<GroupMember> findByGroupIdAndStatus(String groupId, String status);

    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);
    boolean existsByGroupIdAndUserIdAndStatus(String groupId, String userId, String status);
    List<GroupMember> findByUserIdAndStatus(String userId, String status);
    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(
            String groupId,
            String userId,
            String status
    );
    @Query(value = "{ 'groupId': ?0, 'status': 'ACTIVE' }", fields = "{ 'userId': 1 }")
    List<String> findMemberIds(String groupId);

}