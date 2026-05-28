package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CQRS - Query Service for Posts.
 * Optimized for high performance and heavy read loads with Redis Caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostQueryService {

    private final MongoTemplate mongoTemplate;
    private final PostRepository postRepository;
    private final FriendRepository friendRepository;
    private final UserIdentityService userIdentityService;

    @Cacheable(value = "posts-feed", key = "#viewerId != null ? #viewerId : 'anonymous'")
    public List<PostDTO> getAllPosts(String viewerId) {
        log.info("[CQRS-Query] Cache miss. Fetching news feed posts from DB for viewer={}", viewerId);
        Set<String> friendIds = buildFriendIdSet(viewerId);
        List<Document> docs = findPostsAggregated(
                Criteria.where("isDeleted").is(false)
                        .and("isHidden").is(false)
                        .and("groupId").is(null));
        
        List<String> authorIds = docs.stream()
                .map(PostQueryService::extractResolvedAuthorIdFromDoc)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, UserDTO> users = userIdentityService.batchLookupMap(authorIds);
        
        return docs.stream()
                .map(d -> documentToPostDTO(d, users))
                .filter(dto -> isDtoVisibleToViewer(dto, viewerId, friendIds))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "post-detail", key = "#id")
    public PostDTO getPostById(String id) {
        log.info("[CQRS-Query] Cache miss. Fetching post details for id={}", id);
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
                Criteria.where("isHidden").is(false),
                authorMatch
        );
        List<Document> docs = findPostsAggregated(full);
        List<String> authorIds = docs.stream()
                .map(PostQueryService::extractResolvedAuthorIdFromDoc)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        Map<String, UserDTO> users = userIdentityService.batchLookupMap(authorIds);
        return docs.stream()
                .map(d -> documentToPostDTO(d, users))
                .filter(dto -> isDtoVisibleToViewer(dto, viewerId, friendIds))
                .collect(Collectors.toList());
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

    private List<Document> findPostsAggregated(Criteria matchCriteria) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                addResolvedAuthorIdStage(),
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                Aggregation.limit(50)
        );
        return mongoTemplate.aggregate(agg, "posts", Document.class).getMappedResults();
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
        dto.setHidden(doc.getBoolean("isHidden", false));
        dto.setDeleted(doc.getBoolean("isDeleted", false));
        dto.setCreatedAt(toLocalDateTime(doc.get("createdAt")));
        dto.setUpdatedAt(toLocalDateTime(doc.get("updatedAt")));
        return dto;
    }

    private boolean isDtoVisibleToViewer(PostDTO dto, String viewerId, Set<String> friendIds) {
        if (dto == null || dto.getAuthorId() == null) {
            return false;
        }
        if (dto.isHidden()) {
            return dto.getAuthorId().equals(viewerId);
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
