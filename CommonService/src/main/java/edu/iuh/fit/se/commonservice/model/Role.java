package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String name; // USER, ADMIN, MODERATOR
    
    private String description;
    private List<String> permissions; // List of permissions like ["READ_POST", "WRITE_POST", "DELETE_POST", "MANAGE_USERS"]
    private boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

