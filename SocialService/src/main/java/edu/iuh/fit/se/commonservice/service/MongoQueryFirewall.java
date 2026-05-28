package edu.iuh.fit.se.commonservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB Query Firewall — tương đương AiQueryController.java trong file tham khảo.
 * Validate MongoDB Aggregation Pipeline JSON trước khi execute,
 * ngăn chặn injection và write operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MongoQueryFirewall {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    // Whitelist collections (tương đương allowedTables trong tham khảo)
    private static final Set<String> ALLOWED_COLLECTIONS = Set.of(
            "posts", "comments", "reactions", "friends", "friend_requests",
            "notifications", "groups", "group_members", "products",
            "videos", "stories", "saved_posts"
    );

    // Blacklist stages (tương đương chặn UPDATE/DELETE/DROP trong tham khảo)
    private static final Set<String> BLOCKED_STAGES = Set.of(
            "$out", "$merge", "$delete", "$update", "$currentOp",
            "$listSessions", "$planCacheStats", "$changeStream",
            "$collStats", "$indexStats", "$function", "$accumulator",
            "$where"
    );

    // Giới hạn kết quả (tương đương LIMIT 100 trong tham khảo)
    private static final int MAX_LIMIT = 100;

    /**
     * Validate pipeline JSON.
     * Tương đương các kiểm tra trong AiQueryController.executeSql():
     * - Collection thuộc whitelist
     * - Không có blocked stages
     * - Có userId filter trong $match đầu tiên
     * - Có $limit <= MAX_LIMIT
     * - Không có JavaScript ($where, $function)
     * - $lookup chỉ target allowed collections
     *
     * @return null nếu hợp lệ, error message nếu không hợp lệ
     */
    public String validate(List<Document> pipeline, String collection, String userId) {
        // 1. Kiểm tra collection (tương đương allowedTables check)
        if (collection == null || collection.isBlank()) {
            return "Collection name cannot be empty";
        }
        if (!ALLOWED_COLLECTIONS.contains(collection)) {
            return "Collection not allowed: " + collection;
        }

        // 2. Pipeline không rỗng
        if (pipeline == null || pipeline.isEmpty()) {
            return "Pipeline cannot be empty";
        }

        // 3. Kiểm tra từng stage
        boolean hasLimit = false;
        for (int i = 0; i < pipeline.size(); i++) {
            Document stage = pipeline.get(i);
            String stageName = stage.keySet().iterator().next();

            // 3a. Kiểm tra blocked stages (tương đương chặn UPDATE/DELETE/DROP)
            if (BLOCKED_STAGES.contains(stageName)) {
                return "Blocked pipeline stage: " + stageName;
            }

            // 3b. Kiểm tra $limit
            if ("$limit".equals(stageName)) {
                hasLimit = true;
                Object limitVal = stage.get("$limit");
                if (limitVal instanceof Number num && num.intValue() > MAX_LIMIT) {
                    return "$limit exceeds maximum of " + MAX_LIMIT;
                }
            }

            // 3c. Kiểm tra $lookup chỉ target allowed collections
            if ("$lookup".equals(stageName)) {
                Object lookupObj = stage.get("$lookup");
                if (lookupObj instanceof Map<?, ?> lookupMap) {
                    Object fromObj = lookupMap.get("from");
                    if (fromObj instanceof String fromCollection) {
                        if (!ALLOWED_COLLECTIONS.contains(fromCollection)) {
                            return "$lookup targets disallowed collection: " + fromCollection;
                        }
                    }
                }
            }
        }

        // 4. Kiểm tra stage đầu tiên phải là $match chứa userId filter
        // (tương đương student ID / lecturer ID filter check trong tham khảo)
        Document firstStage = pipeline.get(0);
        if (!firstStage.containsKey("$match")) {
            return "Pipeline must start with $match containing user filter";
        }
        Object matchObj = firstStage.get("$match");
        if (!(matchObj instanceof Map)) {
            return "Invalid $match stage format";
        }
        Map<?, ?> matchMap = (Map<?, ?>) matchObj;
        String matchJson;
        try {
            matchJson = objectMapper.writeValueAsString(matchMap).toLowerCase();
        } catch (Exception e) {
            matchJson = matchMap.toString().toLowerCase();
        }

        boolean hasUserFilter = matchJson.contains("authorid")
                || matchJson.contains("userid")
                || matchJson.contains("recipientid")
                || matchJson.contains("sellerid")
                || matchJson.contains("senderid")
                || matchJson.contains("receiverid")
                || matchJson.contains("friendid")
                || matchJson.contains("adminid")
                || matchJson.contains("reporterid");

        if (!hasUserFilter) {
            return "Security Policy Violation: Pipeline must include user context filter (authorId/userId/recipientId/sellerId/senderId/receiverId/adminId)";
        }

        // Kiểm tra userId value có khớp với user hiện tại
        if (userId != null && !userId.isBlank()) {
            if (!matchJson.contains(userId.toLowerCase())) {
                return "Security Policy Violation: User filter must match authenticated user ID";
            }
        }

        // 5. Kiểm tra JavaScript injection (tương đương chặn SQL comments/injection)
        String pipelineJson = pipeline.toString().toLowerCase();
        if (pipelineJson.contains("$where") || pipelineJson.contains("$function")
                || pipelineJson.contains("$accumulator") || pipelineJson.contains("mapreduce")) {
            return "JavaScript expressions are not allowed in pipeline";
        }

        // 6. Auto-append $limit nếu thiếu (tương đương auto LIMIT 100)
        if (!hasLimit) {
            pipeline.add(new Document("$limit", MAX_LIMIT));
            log.info("🔒 [Firewall] Auto-appended $limit {}", MAX_LIMIT);
        }

        return null; // Valid
    }

    /**
     * Parse pipeline JSON string → List<Document>
     */
    public List<Document> parsePipeline(String pipelineJson) {
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(
                    pipelineJson, new TypeReference<List<Map<String, Object>>>() {}
            );
            return rawList.stream()
                    .map(map -> {
                        try {
                            String json = objectMapper.writeValueAsString(map);
                            return Document.parse(json);
                        } catch (Exception e) {
                            return new Document(map);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Invalid pipeline JSON: " + e.getMessage());
        }
    }

    /**
     * Execute validated pipeline.
     * Tương đương jdbcTemplate.queryForList(sql) trong AiQueryController.
     */
    public List<Map<String, Object>> execute(List<Document> pipeline, String collection) {
        try {
            log.info("🔥 [Firewall] Executing pipeline on collection '{}': {} stages", collection, pipeline.size());

            // Convert Documents to AggregationOperations
            List<AggregationOperation> operations = new ArrayList<>();
            for (Document stage : pipeline) {
                operations.add(new AggregationOperation() {
                    @Override
                    public Document toDocument(AggregationOperationContext context) {
                        return stage;
                    }
                });
            }

            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Document> results = mongoTemplate.aggregate(
                    aggregation, collection, Document.class
            );

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (Document doc : results.getMappedResults()) {
                resultList.add(new java.util.LinkedHashMap<>(doc));
            }

            log.info("✅ [Firewall] Query returned {} results", resultList.size());
            return resultList;

        } catch (Exception e) {
            log.error("❌ [Firewall] Error executing pipeline: {}", e.getMessage());
            throw new RuntimeException("Database query error: " + e.getMessage());
        }
    }
}
