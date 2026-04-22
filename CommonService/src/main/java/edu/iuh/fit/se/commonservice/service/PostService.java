package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.DBRef;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
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
        return docs.stream()
                .map(this::documentToPostDTO)
                .filter(dto -> isDtoVisibleToViewer(dto, viewerId, friendIds))
                .collect(Collectors.toList());
    }

    public PostDTO getPostById(String id) {
        return postRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
    }

    public List<PostDTO> getPostsByUserId(String userId) {
        return getPostsByUserId(userId, null);
    }

    public List<PostDTO> getPostsByUserId(String userId, String viewerId) {
        Set<String> friendIds = buildFriendIdSet(viewerId);
        Criteria criteria = Criteria.where("author.$id").is(new ObjectId(userId));
        List<Document> docs = findPostsAggregated(criteria);
        return docs.stream()
                .map(this::documentToPostDTO)
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
        
        // Send socket event
        if (savedDTO.getAuthorId() != null) {
            socketService.notifyPostCreated(
                savedDTO.getAuthorId(),
                SocketEventDTO.postCreated(savedDTO.getAuthorId(), savedDTO)
            );
            
            // Notify friends about new post
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

            // Get author info
            User author = userRepository.findById(post.getAuthorId()).orElse(null);
            if (author == null) return;
            
            // Get all friends
            List<String> friendIds = friendService.getFriendsByUserId(post.getAuthorId())
                    .stream()
                    .map(friend -> friend.getFriendId())
                    .collect(Collectors.toList());
            
            // Create notification for each friend
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
        // Get the original post to share
        Post originalPost = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        // Create new post as a share
        Post sharePost = new Post();
        if (shareDTO.getAuthorId() != null) {
            User author = userRepository.findById(shareDTO.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + shareDTO.getAuthorId()));
            sharePost.setAuthor(author);
        }
        
        // Set share content (user's comment about the share)
        sharePost.setContent(shareDTO.getContent());
        
        // Copy content from original post for display
        // In a real implementation, you might want to store a reference to the original post
        // For now, we'll duplicate the content
        String sharedContent = shareDTO.getContent() != null && !shareDTO.getContent().isEmpty() 
            ? shareDTO.getContent() + "\n\n--- Shared Post ---\n" + originalPost.getContent()
            : "--- Shared Post ---\n" + originalPost.getContent();

        // Chặn share nếu nội dung bị vi phạm (để không tăng shareCount sai).
        aiViolationCheckService.checkOrThrow(sharedContent, "POST");

        // Increment share count on original post
        originalPost.setShareCount(originalPost.getShareCount() + 1);
        postRepository.save(originalPost);

        sharePost.setContent(sharedContent);
        sharePost.setImages(originalPost.getImages());
        sharePost.setVideos(originalPost.getVideos());
        
        // Set share settings
        sharePost.setVisibility(normalizeVisibility(shareDTO.getVisibility()));
        sharePost.setAllowComments(shareDTO.getAllowComments() == null ? Boolean.TRUE : shareDTO.getAllowComments());
        sharePost.setAllowSharing(shareDTO.getAllowSharing() == null ? Boolean.TRUE : shareDTO.getAllowSharing());
        
        // Set timestamps
        sharePost.setCreatedAt(LocalDateTime.now());
        sharePost.setUpdatedAt(LocalDateTime.now());
        sharePost.setDeleted(false);
        
        Post saved = postRepository.save(sharePost);
        PostDTO savedDTO = toDTO(saved);
        
        // Notify original post author about share
        if (originalPost.getAuthor() != null && shareDTO.getAuthorId() != null) {
            String originalAuthorId = originalPost.getAuthor().getId();
            if (!originalAuthorId.equals(shareDTO.getAuthorId())) {
                User sharer = userRepository.findById(shareDTO.getAuthorId()).orElse(null);
                if (sharer != null) {
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
                }
            }
        }
        
        // Send socket event
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
        if (post.getAuthor() != null) {
            dto.setAuthorId(post.getAuthor().getId());
            dto.setAuthorName(post.getAuthor().getFullName());
            dto.setAuthorAvatar(post.getAuthor().getAvatar());
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
            User author = userRepository.findById(dto.getAuthorId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + dto.getAuthorId()));
            post.setAuthor(author);
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
        return post;
    }

    /**
     * Pre-load all friend IDs for the viewer in one query.
     * Friendship may be stored one-way, so we check both directions.
     */
    private Set<String> buildFriendIdSet(String viewerId) {
        if (viewerId == null || viewerId.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        // Single query for both directions
        friendRepository.findByUserIdOrFriendId(viewerId, viewerId).forEach(f -> {
            if (viewerId.equals(f.getUserId())) {
                ids.add(f.getFriendId());
            } else {
                ids.add(f.getUserId());
            }
        });
        return ids;
    }

    // ── Aggregation: batch-resolve authors in 1 query (eliminates @DBRef N+1) ──

    /**
     * Uses MongoDB Aggregation $lookup to batch-join posts with users collection.
     * This replaces N+1 @DBRef resolution with a single server-side join.
     */
    private List<Document> findPostsAggregated(Criteria matchCriteria) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                Aggregation.limit(50),
                Aggregation.lookup("users", "author.$id", "_id", "_authorData"),
                Aggregation.unwind("_authorData", true)
        );
        return mongoTemplate.aggregate(agg, "posts", Document.class).getMappedResults();
    }

    /**
     * Convert a raw aggregation Document (with embedded _authorData) to PostDTO.
     */
    @SuppressWarnings("unchecked")
    private PostDTO documentToPostDTO(Document doc) {
        PostDTO dto = new PostDTO();
        dto.setId(extractId(doc.get("_id")));

        // Author from $lookup result
        Document author = doc.get("_authorData", Document.class);
        if (author != null) {
            dto.setAuthorId(extractId(author.get("_id")));
            dto.setAuthorName(author.getString("fullName"));
            dto.setAuthorAvatar(author.getString("avatar"));
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

        // groupId is a plain field
        dto.setGroupId(doc.getString("groupId"));

        // pageId from @DBRef page (stored as DBRef in raw Document)
        Object pageRef = doc.get("page");
        if (pageRef instanceof DBRef) {
            dto.setPageId(extractId(((DBRef) pageRef).getId()));
        }

        dto.setCreatedAt(toLocalDateTime(doc.get("createdAt")));
        dto.setUpdatedAt(toLocalDateTime(doc.get("updatedAt")));
        return dto;
    }

    /** Visibility check on PostDTO (for aggregation results). */
    private boolean isDtoVisibleToViewer(PostDTO dto, String viewerId, Set<String> friendIds) {
        if (dto == null || dto.getAuthorId() == null) {
            return false;
        }
        String visibility = dto.getVisibility();
        if ("PUBLIC".equals(visibility)) return true;
        if (viewerId == null || viewerId.isBlank()) return false;
        if (dto.getAuthorId().equals(viewerId)) return true;
        if ("FRIENDS".equals(visibility)) return friendIds.contains(dto.getAuthorId());
        return false;
    }

    private static String extractId(Object idValue) {
        if (idValue instanceof ObjectId) return ((ObjectId) idValue).toHexString();
        return idValue != null ? idValue.toString() : null;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        return null;
    }

    private boolean isPostVisibleToViewer(Post post, String viewerId) {
        return isPostVisibleToViewer(post, viewerId, null);
    }

    private boolean isPostVisibleToViewer(Post post, String viewerId, Set<String> preloadedFriendIds) {
        if (post == null || post.getAuthor() == null || post.getAuthor().getId() == null) {
            return false;
        }

        String authorId = post.getAuthor().getId();
        String normalizedVisibility = normalizeVisibility(post.getVisibility());

        if ("PUBLIC".equals(normalizedVisibility)) {
            return true;
        }

        if (viewerId == null || viewerId.isBlank()) {
            return false;
        }

        if (authorId.equals(viewerId)) {
            return true;
        }

        if ("FRIENDS".equals(normalizedVisibility)) {
            if (preloadedFriendIds != null) {
                return preloadedFriendIds.contains(authorId);
            }
            return friendService.checkIfFriends(authorId, viewerId);
        }

        return false;
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

