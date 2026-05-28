package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "livestreams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveStream {
    @Id
    private String id;

    @Indexed
    private String streamerId;
    private String streamerName;
    private String streamerAvatar;

    private String title;
    private String description;

    /** LiveKit room name (unique per active stream) */
    @Indexed(unique = true)
    private String roomName;

    /** Legacy/public key for stream documents (kept for existing DB unique index compatibility) */
    @Indexed(unique = true)
    private String streamKey;

    /** PENDING | LIVE | ENDED */
    @Indexed
    private String status = "PENDING";

    private String thumbnailUrl;

    private int viewerCount = 0;
    private List<String> viewerIds = new ArrayList<>();

    /** If true, host must approve viewers before they can watch */
    private boolean requiresApproval = false;
    private List<String> approvedViewerIds = new ArrayList<>();

    /** Optional: conversation ID for live chat */
    private String chatConversationId;

    /** VIP level of the host at stream creation */
    private int vipLevel = 0;

    /** Max allowed duration in minutes (5, 120, 480, or -1 for unlimited) */
    private int maxLiveDurationMinutes = 5;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
