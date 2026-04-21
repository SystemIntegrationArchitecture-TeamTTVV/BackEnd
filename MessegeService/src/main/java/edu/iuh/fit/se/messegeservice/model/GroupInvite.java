package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "group_invites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "conv_invitee_idx", def = "{'conversationId': 1, 'inviteeId': 1}", unique = true)
public class GroupInvite {
    @Id
    private String id;
    
    private String conversationId;
    private String inviterId;
    private String inviteeId;
    
    // PENDING, ACCEPTED, DECLINED
    private String status = "PENDING";
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
