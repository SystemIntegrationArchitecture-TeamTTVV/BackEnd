package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "coin_packages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoinPackage {
    @Id
    private String id;

    private String name;           // e.g. "Gói 100 xu"
    private int coins;             // number of coins
    private int bonusCoins;        // bonus coins (e.g. 50 extra)
    private long priceVnd;         // price in VND (e.g. 10000 = 10.000đ)
    private String description;    // display description
    private boolean active = true; // visible to users
    private int sortOrder = 0;     // display order

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
