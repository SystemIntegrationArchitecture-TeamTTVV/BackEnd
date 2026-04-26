package edu.iuh.fit.se.commonservice.controller;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.Data;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.model.Comment;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import edu.iuh.fit.se.commonservice.repository.CommentRepository;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private AuthServiceClient authServiceClient;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    /**
     * Get dashboard statistics - Query từ database
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Query từ Auth Service
            Map<String, Object> userMetrics = new HashMap<>();
            try {
                userMetrics = authServiceClient.userMetricsSummary();
            } catch (Exception e) {
                // Fallback
                userMetrics.put("totalUsers", 0L);
                userMetrics.put("activeUsers", 0L);
                userMetrics.put("newUsersThisMonth", 0L);
            }

            List<Post> allPosts = postRepository.findAll();
            List<Comment> allComments = commentRepository.findAll();

            long totalUsers = ((Number) userMetrics.getOrDefault("totalUsers", 0L)).longValue();
            long activeUsers = ((Number) userMetrics.getOrDefault("activeUsers", 0L)).longValue();
            long newUsersThisMonth = ((Number) userMetrics.getOrDefault("newUsersThisMonth", 0L)).longValue();
            long totalPosts = allPosts.size();
            long totalComments = allComments.size();
            long totalViews = allPosts.stream()
                    .mapToLong(p -> p.getLikeCount() != null ? p.getLikeCount() : 0)
                    .sum() * 3; // Estimate: views = likes * 3

            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);
            stats.put("totalPosts", totalPosts);
            stats.put("totalComments", totalComments);
            stats.put("totalViews", totalViews);
            stats.put("newUsersThisMonth", newUsersThisMonth);
            stats.put("engagementRate", calculateEngagementRate(totalPosts, totalComments));

            // Growth statistics mock
            Map<String, Integer> userGrowth = new LinkedHashMap<>();
            stats.put("userGrowth", userGrowth);

        } catch (Exception e) {
            stats.put("error", "Lỗi khi tải thống kê: " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Get top posts by engagement - Query từ database
     */
    @GetMapping("/top-posts")
    public ResponseEntity<List<TopPostDTO>> getTopPosts(@RequestParam(defaultValue = "5") int limit) {
        try {
            List<Post> allPosts = postRepository.findAll();

            List<Post> topPostEntities = allPosts.stream()
                    .sorted((p1, p2) -> Long.compare(
                            (p2.getLikeCount() != null ? p2.getLikeCount() : 0)
                                    + (p2.getCommentCount() != null ? p2.getCommentCount() : 0),
                            (p1.getLikeCount() != null ? p1.getLikeCount() : 0)
                                    + (p1.getCommentCount() != null ? p1.getCommentCount() : 0)))
                    .limit(limit)
                    .toList();

            List<String> authorIds = topPostEntities.stream()
                    .map(Post::getAuthorId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            
            Map<String, UserDTO> usersMap = new HashMap<>();
            if (!authorIds.isEmpty()) {
                try {
                    List<UserDTO> users = authServiceClient.batchLookup(authorIds);
                    if (users != null) {
                        for (UserDTO u : users) {
                            usersMap.put(u.getId(), u);
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            List<TopPostDTO> topPosts = topPostEntities.stream()
                    .map(post -> {
                        String authorName = "Unknown";
                        String authorAvatar = "";
                        if (post.getAuthorId() != null && usersMap.containsKey(post.getAuthorId())) {
                            UserDTO author = usersMap.get(post.getAuthorId());
                            authorName = author.getFullName() != null ? author.getFullName() : author.getUsername();
                            authorAvatar = author.getAvatar() != null ? author.getAvatar() : "";
                        }
                        
                        return new TopPostDTO(
                                post.getId() != null ? post.getId() : "unknown",
                                authorName,
                                authorAvatar,
                                post.getContent() != null ? post.getContent() : "",
                                (post.getLikeCount() != null ? post.getLikeCount() : 0) * 3L,
                                post.getLikeCount() != null ? post.getLikeCount().longValue() : 0L,
                                post.getCommentCount() != null ? post.getCommentCount().longValue() : 0L);
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(topPosts);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    /**
     * Get engagement statistics - Query từ database
     */
    @GetMapping("/engagement")
    public ResponseEntity<Map<String, Object>> getEngagementStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<Post> posts = postRepository.findAll();
            List<Comment> comments = commentRepository.findAll();

            long totalLikes = posts.stream()
                    .mapToLong(p -> p.getLikeCount() != null ? p.getLikeCount() : 0)
                    .sum();
            long totalComments = comments.size();
            long totalShares = posts.stream()
                    .mapToLong(p -> p.getShareCount() != null ? p.getShareCount() : 0)
                    .sum();

            long maxEngagement = Math.max(Math.max(totalLikes, totalComments), totalShares);
            if (maxEngagement == 0) maxEngagement = 1;

            // Engagement metrics (percentage)
            Map<String, Double> engagement = new LinkedHashMap<>();
            engagement.put("Likes", (double) ((totalLikes * 100) / maxEngagement));
            engagement.put("Bình luận", (double) ((totalComments * 100) / maxEngagement));
            engagement.put("Chia sẻ", (double) ((totalShares * 100) / maxEngagement));
            engagement.put("Lượt xem", 100.0);
            stats.put("metrics", engagement);

            // Daily engagement trend (last 7 days from posts)
            Map<String, Integer> dailyEngagement = calculateDailyEngagement(posts, comments);
            stats.put("dailyTrend", dailyEngagement);

        } catch (Exception e) {
            stats.put("error", "Lỗi khi tải thống kê tương tác: " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Get user statistics - Query từ database
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            Map<String, Object> userMetrics = authServiceClient.userMetricsSummary();

            long totalUsers = ((Number) userMetrics.getOrDefault("totalUsers", 0L)).longValue();
            long activeUsers = ((Number) userMetrics.getOrDefault("activeUsers", 0L)).longValue();
            long newUsersThisMonth = ((Number) userMetrics.getOrDefault("newUsersThisMonth", 0L)).longValue();

            double activeUserRate = totalUsers > 0 ? (activeUsers * 100.0) / totalUsers : 0;
            double newUsersRate = totalUsers > 0 ? (newUsersThisMonth * 100.0) / totalUsers : 0;

            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);
            stats.put("newUsersThisMonth", newUsersThisMonth);
            stats.put("activeUserRate", Math.round(activeUserRate * 10.0) / 10.0);
            stats.put("newUsersRate", Math.round(newUsersRate * 10.0) / 10.0);

            // User activity by region (từ dữ liệu user)
            Map<String, Integer> usersByRegion = new LinkedHashMap<>();
            usersByRegion.put("Khác", (int) totalUsers);
            stats.put("byRegion", usersByRegion);

        } catch (Exception e) {
            stats.put("error", "Lỗi khi tải thống kê người dùng: " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Get content statistics - Query từ database
     */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Object>> getContentStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<Post> posts = postRepository.findAll();
            List<Comment> comments = commentRepository.findAll();

            long totalPosts = posts.size();
            long totalComments = comments.size();
            long totalReactions = posts.stream()
                    .mapToLong(p -> (p.getLikeCount() != null ? p.getLikeCount() : 0))
                    .sum();

            double postsPerDay = calculatePostsPerDay(posts);
            double commentsPerDay = calculateCommentsPerDay(comments);

            stats.put("totalPosts", totalPosts);
            stats.put("totalComments", totalComments);
            stats.put("totalReactions", totalReactions);
            stats.put("postsPerDay", Math.round(postsPerDay * 10.0) / 10.0);
            stats.put("commentsPerDay", Math.round(commentsPerDay * 10.0) / 10.0);

            // Content type distribution
            Map<String, Integer> contentTypes = new LinkedHashMap<>();
            contentTypes.put("Text", (int) totalPosts);
            stats.put("byType", contentTypes);

        } catch (Exception e) {
            stats.put("error", "Lỗi khi tải thống kê nội dung: " + e.getMessage());
        }

        return ResponseEntity.ok(stats);
    }

    private double calculateEngagementRate(long totalPosts, long totalComments) {
        if (totalPosts == 0) return 0;
        return Math.round(((double) totalComments / totalPosts) * 10.0) / 10.0;
    }

    private Map<String, Integer> calculateDailyEngagement(List<Post> posts, List<Comment> comments) {
        Map<String, Integer> daily = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = now.minusDays(i).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            int postEngagement = (int) posts.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(dayStart) && p.getCreatedAt().isBefore(dayEnd))
                    .mapToLong(p -> (p.getLikeCount() != null ? p.getLikeCount() : 0)
                            + (p.getCommentCount() != null ? p.getCommentCount() : 0))
                    .sum();
            int commentCount = (int) comments.stream()
                    .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(dayStart) && c.getCreatedAt().isBefore(dayEnd))
                    .count();
            String label = "T" + (7 - i);
            daily.put(label, postEngagement + commentCount);
        }
        return daily;
    }

    private double calculatePostsPerDay(List<Post> posts) {
        if (posts.isEmpty()) return 0;
        return Math.round((double) posts.size() / 30 * 10.0) / 10.0;
    }

    private double calculateCommentsPerDay(List<Comment> comments) {
        if (comments.isEmpty()) return 0;
        return Math.round((double) comments.size() / 30 * 10.0) / 10.0;
    }

    @Data
    public static class TopPostDTO {
        public String id;
        public String authorName;
        public String authorAvatar;
        public String content;
        public long views;
        public long likes;
        public long comments;

        public TopPostDTO(String id, String authorName, String authorAvatar,
                String content, long views, long likes, long comments) {
            this.id = id;
            this.authorName = authorName;
            this.authorAvatar = authorAvatar;
            this.content = content;
            this.views = views;
            this.likes = likes;
            this.comments = comments;
        }
    }
}
