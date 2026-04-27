package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "conv_created_idx", def = "{'conversationId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "conv_deleted_created_idx", def = "{'conversationId': 1, 'isDeleted': 1, 'createdAt': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private String id;

    @DBRef
    private Conversation conversation;
    private String conversationId;

    private String senderId;
    private String senderName;
    private String senderAvatar;

    /** TEXT | SYSTEM | POLL */
    private String messageType = "TEXT";

    /** For SYSTEM messages, e.g. PINNED, UNPINNED, POLL_CREATED. */
    private String systemAction;

    private String content; // text
    private List<String> emojis; // quick reactions on message (emoji codes)

    private List<MessageAttachment> attachments;

    /** Mentioned users parsed from message content tokens like @[Name]. */
    private List<String> mentionUserIds;

    // Message-level flags and metadata
    private boolean pinned = false;              // pinned for the whole conversation
    private List<String> starredByUserIds;       // users who starred this message
    private List<String> seenByUserIds;
    private List<String> deliveredToUserIds;     // users who received the message but haven't read
    private List<String> hiddenForUserIds;

    private boolean isDeleted = false;
    private boolean isEdited = false;

    /** Snapshot of the message this one replies to (same conversation only). */
    private String replyToMessageId;
    private String replyToSenderName;
    private String replyToContentPreview;

    private String pollQuestion;
    private boolean pollMultipleChoice = false;
    private boolean pollClosed = false;
    private boolean pollCanAddOptions = false;
    private boolean pollHideResultsBeforeVote = false;
    private boolean pollHideVoters = false;
    private List<PollOption> pollOptions;
    private LocalDateTime pollDeadline;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

