package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    private String id;

    @Indexed
    private String userId;

    /** donate | receive | deposit */
    private String type;

    private int amount;
    private String giftId;
    private String giftName;
    private String roomId;
    private String senderId;
    private String senderName;
    private String receiverId;
    private String receiverName;

    private String status = "success";
    private String giftMessage;    // optional message with gift
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
