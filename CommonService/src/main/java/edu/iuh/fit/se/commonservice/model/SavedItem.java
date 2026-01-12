package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "saved_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_item_idx", def = "{'userId': 1, 'itemId': 1, 'itemType': 1}", unique = true)
public class SavedItem {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    private String itemId; // ID của post, photo, video, etc.
    private String itemType; // POST, PHOTO, VIDEO, PRODUCT, etc.
    
    private String collection; // Tên collection để phân loại (ví dụ: "Ảnh đẹp", "Công thức nấu ăn")
    
    private LocalDateTime savedAt;
}

