package edu.iuh.fit.se.mediaservice.config;

import edu.iuh.fit.se.mediaservice.model.Gift;
import edu.iuh.fit.se.mediaservice.model.CoinPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@org.springframework.context.annotation.Profile("!test")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Value("${app.data.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            System.out.println("⚠️ MediaService Data Seeding is DISABLED.");
            return;
        }

        System.out.println("🌱 Seeding default gifts & packages into MediaService...");

        // 1. Seed Gifts
        if (mongoTemplate.getCollection("gifts").countDocuments() == 0) {
            LocalDateTime now = LocalDateTime.now();
            List<Gift> gifts = new ArrayList<>();
            gifts.add(new Gift(null, "Hoa hồng", 10, "🌹", null, "popular", true, 1, now, now));
            gifts.add(new Gift(null, "Cà phê", 20, "☕", null, "popular", true, 2, now, now));
            gifts.add(new Gift(null, "Trà sữa", 30, "🧋", null, "popular", true, 3, now, now));
            gifts.add(new Gift(null, "Ngôi sao", 50, "⭐", null, "popular", true, 4, now, now));
            gifts.add(new Gift(null, "Kim cương", 100, "💎", null, "premium", true, 5, now, now));
            gifts.add(new Gift(null, "Siêu xe", 500, "🏎️", null, "premium", true, 6, now, now));
            gifts.add(new Gift(null, "Tên lửa", 1000, "🚀", null, "premium", true, 7, now, now));
            gifts.add(new Gift(null, "Lâu đài", 5000, "🏰", null, "premium", true, 8, now, now));
            mongoTemplate.insertAll(gifts);
            System.out.println("🎁 Seeded default gifts successfully.");
        }

        // 2. Seed CoinPackages
        if (mongoTemplate.getCollection("coin_packages").countDocuments() == 0) {
            LocalDateTime now = LocalDateTime.now();
            List<CoinPackage> packages = new ArrayList<>();
            packages.add(new CoinPackage(null, "Gói Khởi Đầu", 10, 0, 10000L, "Khởi đầu nhẹ nhàng", true, 1, now, now));
            packages.add(new CoinPackage(null, "Gói Phổ Thông", 50, 5, 45000L, "Được ưa chuộng nhất", true, 2, now, now));
            packages.add(new CoinPackage(null, "Gói VIP", 100, 15, 85000L, "Siêu tiết kiệm", true, 3, now, now));
            packages.add(new CoinPackage(null, "Gói Thần Thoại", 500, 100, 400000L, "Nhận ngay đặc quyền", true, 4, now, now));
            mongoTemplate.insertAll(packages);
            System.out.println("💎 Seeded default coin packages successfully.");
        }

        System.out.println("✅ MediaService seeding completed.");
    }
}
