package edu.iuh.fit.se.commonservice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Component để fix conflict index trong MongoDB.
 * Chạy sớm nhất có thể để drop index cũ trước khi Spring Data MongoDB cố tạo index mới.
 */
@Component
@Order(1) // Chạy sớm nhất có thể
public class IndexFixer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(IndexFixer.class);
    private static boolean indexesFixed = false;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
        // Chạy sớm để fix index trước khi Spring Data MongoDB cố tạo
        if (!indexesFixed) {
            fixReactionIndexes();
            indexesFixed = true;
        }
    }

    @Override
    public void run(String... args) {
        // Backup: Nếu PostConstruct không chạy, chạy ở đây
        if (!indexesFixed) {
            fixReactionIndexes();
            indexesFixed = true;
        }
    }

    private void fixReactionIndexes() {
        try {
            logger.info("🔧 Checking and fixing reaction indexes...");
            IndexOperations indexOps = mongoTemplate.indexOps("reactions");
            
            // Lấy danh sách index hiện tại
            List<org.springframework.data.mongodb.core.index.IndexInfo> existingIndexes = indexOps.getIndexInfo();
            
            // Kiểm tra và drop index cũ nếu cần
            for (org.springframework.data.mongodb.core.index.IndexInfo indexInfo : existingIndexes) {
                String indexName = indexInfo.getName();
                
                // Drop user_comment_idx nếu nó có unique constraint (vì sparse + unique với null values gây lỗi)
                if ("user_comment_idx".equals(indexName) && indexInfo.isUnique()) {
                    try {
                        indexOps.dropIndex(indexName);
                        logger.info("✅ Dropped old {} (has unique constraint which causes issues with null values)", indexName);
                    } catch (Exception e) {
                        logger.debug("Could not drop {}: {}", indexName, e.getMessage());
                    }
                }
                
                // Kiểm tra nếu là index cần fix và không có sparse option
                if (("user_post_idx".equals(indexName) || "user_comment_idx".equals(indexName)) 
                    && !indexInfo.isSparse()) {
                    try {
                        indexOps.dropIndex(indexName);
                        logger.info("✅ Dropped old {} (missing sparse option)", indexName);
                    } catch (Exception e) {
                        logger.debug("Could not drop {}: {}", indexName, e.getMessage());
                    }
                }
            }
            
            // Tạo lại index với sparse option nếu chưa tồn tại hoặc đã bị drop
            createIndexIfNotExists(indexOps, "user_post_idx", "userId", "postId");
            createIndexIfNotExists(indexOps, "user_comment_idx", "userId", "commentId");
            
            logger.info("✅ Index fix completed.");
        } catch (Exception e) {
            logger.error("❌ Error fixing indexes: {}", e.getMessage(), e);
        }
    }

    private void createIndexIfNotExists(IndexOperations indexOps, String indexName, String field1, String field2) {
        try {
            // Kiểm tra xem index đã tồn tại chưa
            List<org.springframework.data.mongodb.core.index.IndexInfo> indexes = indexOps.getIndexInfo();
            boolean exists = indexes.stream()
                    .anyMatch(idx -> indexName.equals(idx.getName()) && idx.isSparse());
            
            if (!exists) {
                // Only make user_post_idx unique (one reaction per user per post)
                // user_comment_idx should not be unique because multiple users can react to the same comment
                // and sparse index with null values can cause issues
                Index index = new Index()
                        .on(field1, org.springframework.data.domain.Sort.Direction.ASC)
                        .on(field2, org.springframework.data.domain.Sort.Direction.ASC)
                        .sparse()
                        .named(indexName);
                
                // Only add unique constraint for user_post_idx
                if ("user_post_idx".equals(indexName)) {
                    index = index.unique();
                }
                
                String createdIndexName = indexOps.createIndex(index);
                logger.info("✅ Created {} with sparse option: {}", indexName, createdIndexName);
            } else {
                logger.debug("Index {} already exists with correct configuration", indexName);
            }
        } catch (Exception e) {
           
            logger.debug("Index {} might already exist or will be created by Spring Data MongoDB: {}", 
                    indexName, e.getMessage());
        }
    }
}

