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

    /** Only returned to the stream owner, null for others */
    private String streamKey;

    /** RTMP URL for OBS — only returned to the stream owner */
    private String rtmpUrl;

    private String status;
    private String hlsUrl;
    private String thumbnailUrl;
    private int viewerCount;
    private List<String> viewerIds;
    private String chatConversationId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
