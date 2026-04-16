package edu.iuh.fit.se.messegeservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import edu.iuh.fit.se.messegeservice.model.MessageAttachment;
import edu.iuh.fit.se.messegeservice.model.PollOption;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String messageType;
    private String systemAction;
    private String content;
    private List<String> emojis;
    private List<MessageAttachment> attachments;
    private String pollQuestion;
    private Boolean pollMultipleChoice;
    private Boolean pollClosed;
    private List<PollOption> pollOptions;
    private List<String> mentionUserIds;
    private List<String> seenByUserIds;
    private Boolean pinned;
    private List<String> starredByUserIds;
    
    @JsonProperty(defaultValue = "false")
    private Boolean deleted;
    
    @JsonProperty(defaultValue = "false")
    private Boolean edited;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Client may send this when creating a message; server fills {@link #replyTo}. */
    private String replyToMessageId;

    /** Populated on read / after create — quoted preview for UI. */
    private ReplyToPreview replyTo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyToPreview {
        private String messageId;
        private String senderName;
        private String contentPreview;
    }
}

