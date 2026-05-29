package edu.iuh.fit.se.mediaservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateStreamRequest {
    private String userId;
    private String streamerName;
    private String streamerAvatar;
    private String title;
    private String description;

    /** Duyệt người xem trước khi xem video (waiting room) */
    private Boolean requiresApproval;

    /** URL ảnh cover (tuỳ chọn, có thể upload qua API thumbnail) */
    private String thumbnailUrl;
}
