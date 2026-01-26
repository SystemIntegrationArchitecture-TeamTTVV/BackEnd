package edu.iuh.fit.se.commonservice.dto.story;
import lombok.*;

import java.util.List;

@Data
@Builder
public class StoryResponseDTO {

    private String id;

    private UserDTO user;

    private String contentType;
    private String content;
    private String background;

    private String createdAt;
    private String expiresAt;

    private Boolean isViewed;

    private Boolean isActive;
}

