package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "group_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_group_idx", def = "{'userId': 1, 'groupId': 1}", unique = true)
public class GroupMember {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    @DBRef
    private Group group;
    private String groupId;
    
    private String role = "MEMBER"; // MEMBER, ADMIN, MODERATOR
    private String status = "ACTIVE"; // ACTIVE, PENDING, BANNED
    
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
}

