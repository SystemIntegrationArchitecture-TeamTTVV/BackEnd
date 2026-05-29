package edu.iuh.fit.se.mediaservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "wallet_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletEvent {
    @Id
    private String id;

    @Indexed
    private String walletId;

    @Indexed
    private String userId;

    /** CREDIT | DEBIT */
    private String eventType;

    private int amount;

    private int balanceBefore;

    private int balanceAfter;

    /** DONATE | DEPOSIT | GIFT_RECEIVE */
    private String referenceType;

    private String referenceId; // Transaction or PaymentTransaction ID

    private LocalDateTime createdAt;
}
