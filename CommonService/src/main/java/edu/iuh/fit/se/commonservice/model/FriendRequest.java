package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
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

    private String senderId;
    private String receiverId;

    private String status = "PENDING";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
