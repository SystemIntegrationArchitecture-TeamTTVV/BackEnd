package edu.iuh.fit.se.mediaservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "gifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Gift {
    @Id
    private String id;
    private String name;
    private int price;       // coin price
    private String emoji;    // emoji icon e.g. 🌹
    private String imageUrl; // optional image URL
    private String category; // e.g. "popular", "premium"
    private boolean active = true;  // admin can hide/show gifts
    private int sortOrder = 0;      // display ordering

    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
