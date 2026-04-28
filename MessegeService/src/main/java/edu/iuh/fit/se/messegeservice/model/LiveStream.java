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

    @Indexed(unique = true)
    private String streamKey;

    /** PENDING | LIVE | ENDED */
    @Indexed
    private String status = "PENDING";

    /** Full HLS URL for playback, e.g. http://host:8888/live/<streamKey>/index.m3u8 */
    private String hlsUrl;

    private String thumbnailUrl;

    private int viewerCount = 0;
    private List<String> viewerIds = new ArrayList<>();

    /** Optional: conversation ID for live chat */
    private String chatConversationId;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
