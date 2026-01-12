package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "friends")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_friend_idx", def = "{'userId': 1, 'friendId': 1}", unique = true)
public class Friend {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    @DBRef
    private User friend;
    private String friendId;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

