package edu.iuh.fit.se.commonservice.dto.story;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class StoryResponseDTO {

    private String id;

    private UserDTO user;

    private String contentType;
    private String content;
    private String background;
    /** Chú thích ảnh/video */
    private String caption;

    private String createdAt;
    private String expiresAt;

    private Boolean isViewed;

    private Boolean isActive;

    /** Tổng số người đã xem */
    private int viewCount;

    /** Reaction counts: emoji -> count */
    private Map<String, Integer> reactions;

    /** userId của những người đã xem (chỉ trả cho chủ story) */
    private List<String> viewers;
}

