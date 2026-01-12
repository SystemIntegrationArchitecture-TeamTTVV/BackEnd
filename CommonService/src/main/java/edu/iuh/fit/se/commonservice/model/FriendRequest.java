package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "friend_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "sender_receiver_idx", def = "{'senderId': 1, 'receiverId': 1}", unique = true)
public class FriendRequest {
    @Id
    private String id;
    
    @DBRef
    private User sender;
    private String senderId;
    
    @DBRef
    private User receiver;
    private String receiverId;
    
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, CANCELLED
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

