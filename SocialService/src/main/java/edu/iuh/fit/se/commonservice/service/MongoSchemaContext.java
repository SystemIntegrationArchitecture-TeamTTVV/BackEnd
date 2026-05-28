package edu.iuh.fit.se.commonservice.service;

/**
 * MongoDB Schema Context — tương đương SCHEMA_CONTEXT trong StudentChatService.java tham khảo.
 * Mô tả toàn bộ schema MongoDB cho LLM hiểu cấu trúc data để sinh Aggregation Pipeline.
 */
public final class MongoSchemaContext {

    private MongoSchemaContext() {}

    public static final String SCHEMA = """
        MongoDB Schema Context (SocialService, camelCase field names, MongoDB Aggregation Pipeline):

        Allowed collections for DATA_QUERY:

        1. `posts` — Bài đăng
           - _id (ObjectId auto, truy vấn bằng string id)
           - authorId (String, FK → user id, BẮT BUỘC lọc)
           - content (String), images (List<String>), videos (List<String>)
           - location (String), feeling (String), activity (String)
           - visibility (String enum: PUBLIC | FRIENDS | PRIVATE)
           - likeCount (Integer default 0), commentCount (Integer default 0), shareCount (Integer default 0)
           - groupId (String, nullable)
           - createdAt (ISODate), updatedAt (ISODate)
           - isDeleted (Boolean default false), isHidden (Boolean default false), isPinned (Boolean default false)

        2. `comments` — Bình luận
           - _id, postId (String FK → posts._id), authorId (String FK → user id)
           - authorName (String), content (String), images (List<String>)
           - parentCommentId (String nullable, self-ref cho reply)
           - likeCount (Integer default 0), replyCount (Integer default 0)
           - createdAt (ISODate), isDeleted (Boolean default false)

        3. `reactions` — Cảm xúc
           - _id, userId (String FK → user id)
           - postId (String nullable FK → posts._id)
           - commentId (String nullable FK → comments._id)
           - videoId (String nullable FK → videos._id)
           - type (String enum: LIKE | LOVE | HAHA | WOW | SAD | ANGRY)
           - createdAt (ISODate)

        4. `friends` — Quan hệ bạn bè (2 chiều, mỗi cặp bạn bè có 2 documents)
           - _id, userId (String), friendId (String)
           - createdAt (ISODate)

        5. `friend_requests` — Lời mời kết bạn
           - _id, senderId (String), receiverId (String)
           - status (String enum: PENDING | ACCEPTED | REJECTED)
           - createdAt (ISODate)

        6. `notifications` — Thông báo
           - _id, recipientId (String FK → user id, BẮT BUỘC lọc)
           - actorId (String FK → user id)
           - type (String enum: LIKE_POST | COMMENT_POST | FRIEND_REQUEST | FRIEND_ACCEPTED | GROUP_INVITE | ...)
           - title (String), content (String), image (String URL)
           - relatedId (String nullable), relatedType (String enum: POST | COMMENT | GROUP | EVENT | ...)
           - isRead (Boolean default false), createdAt (ISODate)

        7. `groups` — Nhóm
           - _id, name (String), description (String), adminId (String FK → user id)
           - privacy (String enum: PUBLIC | PRIVATE | SECRET)
           - memberCount (Integer default 0), postCount (Integer default 0)
           - createdAt (ISODate), isActive (Boolean default true)

        8. `group_members` — Thành viên nhóm
           - _id, userId (String FK → user id), groupId (String FK → groups._id)
           - role (String enum: MEMBER | ADMIN | MODERATOR)
           - status (String enum: ACTIVE | PENDING | BANNED | REJECT)
           - joinedAt (ISODate)

        9. `products` — Sản phẩm Marketplace
           - _id, title (String), description (String), sellerId (String FK → user id)
           - price (Decimal), currency (String default "USD"), condition (String)
           - location (String), category (String), images (List<String>), tags (List<String>)
           - isSold (Boolean default false), isActive (Boolean default true)
           - createdAt (ISODate)

        10. `videos` — Video
            - _id, authorId (String FK → user id)
            - title (String), description (String), videoUrl (String), thumbnailUrl (String)
            - viewCount (Integer default 0), likeCount (Integer default 0)
            - commentCount (Integer default 0), shareCount (Integer default 0)
            - visibility (String enum: PUBLIC | FRIENDS | PRIVATE)
            - createdAt (ISODate), isDeleted (Boolean default false)

        11. `stories` — Stories (tự xóa sau 24h)
            - _id, userId (String FK → user id), userName (String), userAvatar (String)
            - contentType (String: image | text | video), content (String)
            - viewers (List<String> userIds), reactions (Map<String, Integer>)
            - createdAt (ISODate), expiredAt (ISODate)

        12. `saved_posts` — Bài đã lưu
            - _id, userId (String FK → user id), postId (String FK → posts._id)
            - savedAt (ISODate)

        Relationships (reference bằng ID field, dùng $lookup khi cần join):
        - posts.authorId → user id (lọc bài của user)
        - comments.postId → posts._id (comment thuộc bài nào)
        - comments.authorId → user id (ai comment)
        - reactions.postId → posts._id (react bài nào)
        - reactions.userId → user id (ai react)
        - friends.userId + friends.friendId → cặp bạn bè 2 chiều
        - notifications.recipientId → user id (thông báo cho ai)
        - group_members.userId → user id, group_members.groupId → groups._id
        - saved_posts.userId → user id, saved_posts.postId → posts._id

        RULES BẮT BUỘC khi sinh pipeline:
        1. Output PHẢI là JSON hợp lệ: {"collection": "<tên>", "pipeline": [<stages>]}
        2. Pipeline PHẢI bắt đầu bằng $match chứa userId/authorId/recipientId/sellerId = '<userId>'
        3. Luôn thêm {isDeleted: false} khi query posts, comments, videos
        4. KHÔNG dùng: $out, $merge, $delete, $update, $function, $where, $accumulator
        5. PHẢI có $limit cuối pipeline, tối đa 100
        6. Khi đếm: dùng $count hoặc {$group: {_id: null, count: {$sum: 1}}}
        7. Khi lọc ngày: dùng format {"$date": "2025-05-01T00:00:00Z"}, ví dụ {createdAt: {$gte: {"$date": "2025-05-01T00:00:00Z"}}}
        8. $lookup chỉ được target các collection trong danh sách trên
        9. Dùng $project để chọn cột cần thiết, KHÔNG trả toàn bộ document
        10. Không dùng $graphLookup, $currentOp, $listSessions
        """;
}
