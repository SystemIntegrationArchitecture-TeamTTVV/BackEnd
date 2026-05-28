package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveStreamDTO {
    private String id;
    private String streamerId;
    private String streamerName;
    private String streamerAvatar;
    private String title;
    private String description;

    private String roomName;
    private String status;
    private String thumbnailUrl;
    private int viewerCount;
    private List<String> viewerIds;
    private String chatConversationId;

    private boolean requiresApproval;

    /** Chỉ host mới nhận danh sách khi gọi getStreamById với userId = streamer */
    private List<String> approvedViewerIds;

    /** Trả về kèm token: host hay viewer */
    private Boolean isHost;
    /** false = chờ duyệt (waiting room), chỉ có sau getToken */
    private Boolean canSubscribe;
    /** APPROVED | WAITING — gợi ý UI trước/sau token */
    private String joinStatus;

    /** LiveKit connection token — only returned when joining */
    private String livekitToken;
    /** LiveKit server URL — only returned when joining */
    private String livekitUrl;

    /** VIP level of the host */
    private int vipLevel;

    /** Max allowed duration in minutes */
    private int maxLiveDurationMinutes;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
