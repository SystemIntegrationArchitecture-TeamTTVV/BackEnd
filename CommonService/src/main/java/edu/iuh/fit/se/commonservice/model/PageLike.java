package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "page_likes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_page_idx", def = "{'userId': 1, 'pageId': 1}", unique = true)
public class PageLike {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    @DBRef
    private Page page;
    private String pageId;
    
    private boolean isFollowing = true; // Theo dõi trang
    
    private LocalDateTime createdAt;
}

