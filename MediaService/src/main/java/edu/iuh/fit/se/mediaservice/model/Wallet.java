package edu.iuh.fit.se.mediaservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private int balance = 1000; // default starting balance

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
