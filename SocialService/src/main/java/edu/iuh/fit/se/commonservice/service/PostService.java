package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.DBRef;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserIdentityService userIdentityService;
    private final FriendRepository friendRepository;
    private final MongoTemplate mongoTemplate;
    private final SocketService socketService;
    private final NotificationService notificationService;
    private final FriendService friendService;
    private final AIViolationCheckService aiViolationCheckService;

    public List<PostDTO> getAllPosts() {
        return getAllPosts(null);
    }

    public List<PostDTO> getAllPosts(String viewerId) {
        Set<String> friendIds = buildFriendIdSet(viewerId);
        List<Document> docs = findPostsAggregated(
                Criteria.where("isDeleted").is(false).and("groupId").is(null));
        List<String> authorIds = docs.stream()
                .map(PostService::extractResolvedAuthorIdFromDoc)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, UserDTO> users = userIdentityService.batchLookupMap(authorIds);
        return docs.stream()
                .map(d -> documentToPostDTO(d, users))
                .filter(dto -> isDtoVisibleToViewer(dto, viewerId, friendIds))
                .collect(Collectors.toList());
    }

    public PostDTO getPostById(String id) {
        Document doc = mongoTemplate.findById(id, Document.class, "posts");
        if (doc == null) {
            throw new RuntimeException("Post not found with id: " + id);
        }
        String aid = extractResolvedAuthorIdFromDoc(doc);
        Map<String, UserDTO> users = aid != null && !aid.isBlank()
                ? userIdentityService.batchLookupMap(List.of(aid))
                : Collections.emptyMap();
        return documentToPostDTO(doc, users);
    }

    public List<PostDTO> getPostsByUserId(String userId) {
        return getPostsByUserId(userId, null);
    }

    public List<PostDTO> getPostsByUserId(String userId, String viewerId) {
        Set<String> friendIds = buildFriendIdSet(viewerId);
        List<Criteria> authorOr = new ArrayList<>();
        authorOr.add(Criteria.where("authorId").is(userId));
        if (ObjectId.isValid(userId)) {
            authorOr.add(Criteria.where("author.$id").is(new ObjectId(userId)));
        }
        Criteria authorMatch = new Criteria().orOperator(authorOr.toArray(new Criteria[0]));
        Criteria full = new Criteria().andOperator(
                Criteria.where("isDeleted").is(false),
                authorMatch
        );
        List<Document> docs = findPostsAggregated(full);
        List<String> authorIds = docs.stream()
                .map(PostService::extractResolvedAuthorIdFromDoc)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        Map<String, UserDTO> users = userIdentityService.batchLookupMap(authorIds);
        return docs.stream()
                .map(d -> documentToPostDTO(d, users))
                .filter(dto -> isDtoVisibleToViewer(dto, viewerId, friendIds))
                .collect(Collectors.toList());
    }

    public List<PostDTO> getPostsByGroupId(String groupId) {
        return postRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PostDTO> getPostsByPageId(String pageId) {
        return postRepository.findByPageIdOrderByCreatedAtDesc(pageId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PostDTO createPost(PostDTO postDTO) {
        aiViolationCheckService.checkOrThrow(postDTO.getContent(), "POST");

        Post post = toEntity(postDTO);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setDeleted(false);
        Post saved = postRepository.save(post);
        PostDTO savedDTO = toDTO(saved);

        if (savedDTO.getAuthorId() != null) {
            socketService.notifyPostCreated(
                    savedDTO.getAuthorId(),
                    SocketEventDTO.postCreated(savedDTO.getAuthorId(), savedDTO)
            );
            notifyFriendsAboutPost(savedDTO);
        }

        return savedDTO;
    }

    private void notifyFriendsAboutPost(PostDTO post) {
        try {
            String visibility = normalizeVisibility(post.getVisibility());
            if ("PRIVATE".equals(visibility)) {
                return;
            }

            UserDTO author = userIdentityService.findById(post.getAuthorId()).orElse(null);
            if (author == null) {
                return;
            }

            List<String> friendIds = friendService.getFriendsByUserId(post.getAuthorId())
                    .stream()
                    .map(friend -> friend.getFriendId())
                    .collect(Collectors.toList());

            for (String friendId : friendIds) {
                NotificationDTO notificationDTO = new NotificationDTO();
                notificationDTO.setType("POST");
                notificationDTO.setActorId(post.getAuthorId());
                notificationDTO.setActorName(author.getFullName());
                notificationDTO.setActorAvatar(author.getAvatar());
                notificationDTO.setRecipientId(friendId);
                notificationDTO.setRelatedId(post.getId());
                notificationDTO.setRelatedType("POST");
                notificationDTO.setTitle("Bài viết mới");
                notificationDTO.setContent(author.getFullName() + " đã đăng bài viết mới");
                notificationDTO.setRead(false);
                notificationDTO.setCreatedAt(LocalDateTime.now());

                notificationService.createNotification(notificationDTO);
            }
        } catch (Exception e) {
            System.err.println("Failed to notify friends about post: " + e.getMessage());
        }
    }

    public PostDTO updatePost(String id, PostDTO postDTO) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        aiViolationCheckService.checkOrThrow(postDTO.getContent(), "POST");

        post.setContent(postDTO.getContent());
        post.setImages(postDTO.getImages());
        post.setVideos(postDTO.getVideos());
        post.setLocation(postDTO.getLocation());
        post.setFeeling(postDTO.getFeeling());
        post.setActivity(postDTO.getActivity());
        if (postDTO.getVisibility() != null) {
            post.setVisibility(normalizeVisibility(postDTO.getVisibility()));
        }
        post.setUpdatedAt(LocalDateTime.now());

        Post updated = postRepository.save(post);
        return toDTO(updated);
    }

    public void deletePost(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    public PostDTO sharePost(String postId, PostDTO shareDTO) {
        Post originalPost = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        Post sharePost = new Post();
        if (shareDTO.getAuthorId() != null) {
            userIdentityService.getByIdOrThrow(shareDTO.getAuthorId());
            sharePost.setAuthorId(shareDTO.getAuthorId());
        }

        String sharedContent = shareDTO.getContent() != null && !shareDTO.getContent().isEmpty()
                ? shareDTO.getContent() + "\n\n--- Shared Post ---\n" + originalPost.getContent()
                : "--- Shared Post ---\n" + originalPost.getContent();

        aiViolationCheckService.checkOrThrow(sharedContent, "POST");

        originalPost.setShareCount(originalPost.getShareCount() + 1);
        postRepository.save(originalPost);

        sharePost.setContent(sharedContent);
        sharePost.setImages(originalPost.getImages());
        sharePost.setVideos(originalPost.getVideos());
        sharePost.setVisibility(normalizeVisibility(shareDTO.getVisibility()));
        sharePost.setAllowComments(shareDTO.getAllowComments() == null ? Boolean.TRUE : shareDTO.getAllowComments());
        sharePost.setAllowSharing(shareDTO.getAllowSharing() == null ? Boolean.TRUE : shareDTO.getAllowSharing());
        sharePost.setCreatedAt(LocalDateTime.now());
        sharePost.setUpdatedAt(LocalDateTime.now());
        sharePost.setDeleted(false);

        Post saved = postRepository.save(sharePost);
        PostDTO savedDTO = toDTO(saved);

        Document origDoc = mongoTemplate.findById(postId, Document.class, "posts");
        String originalAuthorId = origDoc != null ? extractResolvedAuthorIdFromDoc(origDoc) : originalPost.getAuthorId();
        if (originalAuthorId != null && shareDTO.getAuthorId() != null
                && !originalAuthorId.equals(shareDTO.getAuthorId())) {
            userIdentityService.findById(shareDTO.getAuthorId()).ifPresent(sharer -> {
                NotificationDTO notificationDTO = new NotificationDTO();
                notificationDTO.setRecipientId(originalAuthorId);
                notificationDTO.setActorId(shareDTO.getAuthorId());
                notificationDTO.setActorName(sharer.getFullName());
                notificationDTO.setActorAvatar(sharer.getAvatar());
                notificationDTO.setType("SHARE_POST");
                notificationDTO.setTitle("Post Shared");
                notificationDTO.setContent(sharer.getFullName() + " shared your post");
                notificationDTO.setRelatedId(postId);
                notificationDTO.setRelatedType("POST");
                notificationService.createNotification(notificationDTO);
            });
        }

        if (savedDTO.getAuthorId() != null) {
            socketService.notifyPostCreated(
                    savedDTO.getAuthorId(),
                    SocketEventDTO.postCreated(savedDTO.getAuthorId(), savedDTO)
            );
        }

        return savedDTO;
    }

    private PostDTO toDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        String aid = post.getAuthorId();
        if (aid != null && !aid.isBlank()) {
            dto.setAuthorId(aid);
            userIdentityService.findById(aid).ifPresent(u -> {
                dto.setAuthorName(u.getFullName());
                dto.setAuthorAvatar(u.getAvatar());
            });
        }
        dto.setContent(post.getContent());
        dto.setImages(post.getImages());
        dto.setVideos(post.getVideos());
        dto.setLocation(post.getLocation());
        dto.setFeeling(post.getFeeling());
        dto.setActivity(post.getActivity());
        dto.setVisibility(normalizeVisibility(post.getVisibility()));
        dto.setAllowComments(post.getAllowComments());
        dto.setAllowSharing(post.getAllowSharing());
        dto.setLikeCount(post.getLikeCount());
        dto.setCommentCount(post.getCommentCount());
        dto.setShareCount(post.getShareCount());
        if (post.getGroup() != null) {
            dto.setGroupId(post.getGroup().getId());
        }
        if (post.getPage() != null) {
            dto.setPageId(post.getPage().getId());
        }
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    private Post toEntity(PostDTO dto) {
        Post post = new Post();
        if (dto.getAuthorId() != null) {
            userIdentityService.getByIdOrThrow(dto.getAuthorId());
            post.setAuthorId(dto.getAuthorId());
        }
        post.setContent(dto.getContent());
        post.setImages(dto.getImages());
        post.setVideos(dto.getVideos());
        post.setLocation(dto.getLocation());
        post.setFeeling(dto.getFeeling());
        post.setActivity(dto.getActivity());
        post.setVisibility(normalizeVisibility(dto.getVisibility()));
        post.setAllowComments(dto.getAllowComments() == null ? Boolean.TRUE : dto.getAllowComments());
        post.setAllowSharing(dto.getAllowSharing() == null ? Boolean.TRUE : dto.getAllowSharing());
        post.setGroupId(dto.getGroupId());
        return post;
    }

    private Set<String> buildFriendIdSet(String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        friendRepository.findByUserIdOrFriendId(viewerId, viewerId).forEach(f -> {
            if (viewerId.equals(f.getUserId())) {
                ids.add(f.getFriendId());
            } else {
                ids.add(f.getUserId());
            }
        });
        return ids;
    }

    private AggregationOperation addResolvedAuthorIdStage() {
        return context -> new Document("$addFields", new Document("_resolvedAuthorId",
                new Document("$ifNull", List.of(
                        "$authorId",
                        new Document("$convert", new Document("input", "$author.$id")
                                .append("to", "string")
                                .append("onError", "")
                                .append("onNull", ""))
                ))
        ));
    }

    private List<Document> findPostsAggregated(Criteria matchCriteria) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                addResolvedAuthorIdStage(),
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                Aggregation.limit(50)
        );
        return mongoTemplate.aggregate(agg, "posts", Document.class).getMappedResults();
    }

    private static String extractResolvedAuthorIdFromDoc(Document doc) {
        if (doc == null) {
            return null;
        }
        Object v = doc.get("_resolvedAuthorId");
        if (v != null && !v.toString().isBlank()) {
            return v.toString();
        }
        String direct = doc.getString("authorId");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        Object author = doc.get("author");
        if (author instanceof DBRef ref && ref.getId() != null) {
            return extractId(ref.getId());
        }
        return null;
    }

    private PostDTO documentToPostDTO(Document doc, Map<String, UserDTO> userMap) {
        PostDTO dto = new PostDTO();
        dto.setId(extractId(doc.get("_id")));

        String aid = extractResolvedAuthorIdFromDoc(doc);
        dto.setAuthorId(aid);
        if (aid != null && userMap != null) {
            UserDTO u = userMap.get(aid);
            if (u != null) {
                dto.setAuthorName(u.getFullName());
                dto.setAuthorAvatar(u.getAvatar());
            }
        }

        dto.setContent(doc.getString("content"));
        dto.setImages(doc.getList("images", String.class));
        dto.setVideos(doc.getList("videos", String.class));
        dto.setLocation(doc.getString("location"));
        dto.setFeeling(doc.getString("feeling"));
        dto.setActivity(doc.getString("activity"));
        dto.setVisibility(normalizeVisibility(doc.getString("visibility")));
        dto.setAllowComments(doc.getBoolean("allowComments"));
        dto.setAllowSharing(doc.getBoolean("allowSharing"));
        dto.setLikeCount(doc.getInteger("likeCount", 0));
        dto.setCommentCount(doc.getInteger("commentCount", 0));
        dto.setShareCount(doc.getInteger("shareCount", 0));
        dto.setGroupId(doc.getString("groupId"));
        Object pageRef = doc.get("page");
        if (pageRef instanceof DBRef) {
            dto.setPageId(extractId(((DBRef) pageRef).getId()));
        }
        dto.setCreatedAt(toLocalDateTime(doc.get("createdAt")));
        dto.setUpdatedAt(toLocalDateTime(doc.get("updatedAt")));
        return dto;
    }

    private boolean isDtoVisibleToViewer(PostDTO dto, String viewerId, Set<String> friendIds) {
        if (dto == null || dto.getAuthorId() == null) {
            return false;
        }
        String visibility = dto.getVisibility();
        if ("PUBLIC".equals(visibility)) {
            return true;
        }
        if (viewerId == null || viewerId.isBlank()) {
            return false;
        }
        if (dto.getAuthorId().equals(viewerId)) {
            return true;
        }
        if ("FRIENDS".equals(visibility)) {
            return friendIds.contains(dto.getAuthorId());
        }
        return false;
    }

    private static String extractId(Object idValue) {
        if (idValue instanceof ObjectId) {
            return ((ObjectId) idValue).toHexString();
        }
        return idValue != null ? idValue.toString() : null;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "PUBLIC";
        }
        String normalized = visibility.trim().toUpperCase();
        return switch (normalized) {
            case "FRIEND", "FRIENDS" -> "FRIENDS";
            case "ONLY_ME", "PRIVATE" -> "PRIVATE";
            case "PUBLIC" -> "PUBLIC";
            default -> "PUBLIC";
        };
    }
}
