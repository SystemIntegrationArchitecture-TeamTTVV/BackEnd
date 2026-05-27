package edu.iuh.fit.se.commonservice.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import edu.iuh.fit.se.commonservice.model.QueryExample;
import edu.iuh.fit.se.commonservice.repository.QueryExampleRepository;
import edu.iuh.fit.se.commonservice.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Startup seeder cho query_examples (RAG few-shot).
 * Tự động tạo vector embedding cho 8 mẫu câu hỏi phổ biến nếu DB trống.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class QueryExampleSeeder implements CommandLineRunner {

    private final QueryExampleRepository queryExampleRepository;
    private final EmbeddingService embeddingService;

    @Override
    public void run(String... args) {
        try {
            long count = queryExampleRepository.count();

            // Nếu đã có data nhưng embedding bị sai kích thước (cũ dùng Gemini 768, mới dùng local 512)
            // thì xóa hết để seed lại cho đúng
            if (count > 0) {
                QueryExample sample = queryExampleRepository.findAll().stream().findFirst().orElse(null);
                if (sample != null && sample.getEmbedding() != null && sample.getEmbedding().size() != 768) {
                    log.info("🔄 [QueryExampleSeeder] Old embeddings detected (dim={}), clearing for re-seed.",
                            sample.getEmbedding().size());
                    queryExampleRepository.deleteAll();
                    count = 0;
                }
            }

            if (count > 0) {
                log.info("📊 [QueryExampleSeeder] RAG database already has {} examples, skipping seeding.", count);
                return;
            }

            log.info("🚀 [QueryExampleSeeder] Seeding RAG few-shot examples with LOCAL embedding (no API key needed)...");

            seedExample(
                    "Tôi đã đăng bao nhiêu bài viết?",
                    "posts",
                    "[{\"$match\":{\"authorId\":\"USER_ID\",\"isDeleted\":false}},{\"$count\":\"total\"}]"
            );

            seedExample(
                    "Bài đăng nào của tôi được nhiều lượt thích nhất?",
                    "posts",
                    "[{\"$match\":{\"authorId\":\"USER_ID\",\"isDeleted\":false}},{\"$sort\":{\"likeCount\":-1}},{\"$limit\":1}]"
            );

            seedExample(
                    "Tôi có bao nhiêu bạn bè?",
                    "friends",
                    "[{\"$match\":{\"userId\":\"USER_ID\"}},{\"$count\":\"total\"}]"
            );

            seedExample(
                    "Ai tương tác (react) nhiều nhất với bài viết của tôi?",
                    "reactions",
                    "[{\"$lookup\":{\"from\":\"posts\",\"localField\":\"postId\",\"foreignField\":\"_id\",\"as\":\"postInfo\"}},{\"$unwind\":\"$postInfo\"},{\"$match\":{\"postInfo.authorId\":\"USER_ID\",\"userId\":{\"$ne\":\"USER_ID\"}}},{\"$group\":{\"_id\":\"$userId\",\"count\":{\"$sum\":1}}},{\"$sort\":{\"count\":-1}},{\"$limit\":5}]"
            );

            seedExample(
                    "Có ai bình luận vào bài đăng của tôi gần đây không?",
                    "comments",
                    "[{\"$lookup\":{\"from\":\"posts\",\"localField\":\"postId\",\"foreignField\":\"_id\",\"as\":\"postInfo\"}},{\"$unwind\":\"$postInfo\"},{\"$match\":{\"postInfo.authorId\":\"USER_ID\",\"authorId\":{\"$ne\":\"USER_ID\"},\"isDeleted\":false}},{\"$sort\":{\"createdAt\":-1}},{\"$limit\":10}]"
            );

            seedExample(
                    "Những thông báo chưa đọc của tôi là gì?",
                    "notifications",
                    "[{\"$match\":{\"recipientId\":\"USER_ID\",\"isRead\":false}},{\"$sort\":{\"createdAt\":-1}},{\"$limit\":10}]"
            );

            seedExample(
                    "Liệt kê các sản phẩm tôi đang đăng bán trên chợ?",
                    "products",
                    "[{\"$match\":{\"sellerId\":\"USER_ID\",\"isActive\":true}},{\"$sort\":{\"createdAt\":-1}},{\"$limit\":10}]"
            );

            seedExample(
                    "Những bài viết tôi đã lưu gần đây?",
                    "saved_posts",
                    "[{\"$match\":{\"userId\":\"USER_ID\"}},{\"$lookup\":{\"from\":\"posts\",\"localField\":\"postId\",\"foreignField\":\"_id\",\"as\":\"post\"}},{\"$unwind\":\"$post\"},{\"$sort\":{\"savedAt\":-1}},{\"$limit\":10}]"
            );

            log.info("✅ [QueryExampleSeeder] Successfully seeded standard query examples!");

        } catch (Exception e) {
            log.error("❌ [QueryExampleSeeder] Failed to seed examples: {}", e.getMessage(), e);
        }
    }

    private void seedExample(String question, String collection, String pipeline) {
        try {
            log.debug("🧬 [QueryExampleSeeder] Embedding for seeder: {}", question);
            List<Double> vector = embeddingService.embed(question);
            if (vector.isEmpty()) {
                log.warn("⚠️ [QueryExampleSeeder] Could not get vector for '{}', skipped", question);
                return;
            }

            QueryExample example = new QueryExample();
            example.setQuestion(question);
            example.setPipeline(pipeline);
            example.setCollection(collection);
            example.setEmbedding(vector);
            example.setSuccess(true);
            example.setCreatedAt(LocalDateTime.now());

            queryExampleRepository.save(example);
        } catch (Exception e) {
            log.error("❌ [QueryExampleSeeder] Error seeding '{}': {}", question, e.getMessage());
        }
    }
}
