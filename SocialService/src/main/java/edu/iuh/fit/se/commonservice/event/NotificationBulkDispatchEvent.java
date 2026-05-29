package edu.iuh.fit.se.commonservice.event;

import java.util.List;

public record NotificationBulkDispatchEvent(
    String type,
    String actorId,
    String actorName,
    String actorAvatar,
    List<String> recipientIds,
    String relatedId,
    String relatedType,
    String title,
    String content
) {}
