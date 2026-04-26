package edu.iuh.fit.se.authservice.config;

import edu.iuh.fit.se.authservice.entity.RoleEntity;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.repository.RoleRepository;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.data.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("⚠️ Data seeding is DISABLED. Set 'app.data.seed.enabled=true' to enable.");
            return;
        }

        if (roleRepository.count() > 0 && userRepository.count() > 0) {
            log.info("✅ Data already exists in AuthService. Skipping seed.");
            return;
        }

        log.info("🌱 Starting DataSeeder for AuthService...");

        // 1. Seed Roles
        RoleEntity userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.saveAndFlush(RoleEntity.builder().name("ROLE_USER").description("Regular User").build()));

        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.saveAndFlush(RoleEntity.builder().name("ROLE_ADMIN").description("Administrator").build()));

        // 2. Seed Users via JdbcTemplate to bypass @GeneratedValue and force IDs like 'u1', 'u2', 'u3'
        String encodedPassword = passwordEncoder.encode("password123");
        LocalDateTime now = LocalDateTime.now();

        // Check if users exist using JDBC just in case
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id IN ('u1', 'u2', 'u3', 'u4', 'u5', 'u6', 'u7', 'u8', 'u9', 'u10')", Integer.class);
        if (count == null || count < 10) {
            String insertUserSql = "INSERT INTO users " +
                    "(id, email, username, password, first_name, last_name, full_name, avatar, is_active, is_verified, role_id, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";

            jdbcTemplate.update(insertUserSql,
                    "u1", "lan@example.com", "lannguyen", encodedPassword, "Lan", "Nguyễn Thị", "Nguyễn Thị Lan", "https://picsum.photos/400/400?random=70",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u2", "minh@example.com", "minhtran", encodedPassword, "Minh", "Trần Văn", "Trần Văn Minh", "https://picsum.photos/400/400?random=71",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u3", "hong@example.com", "hongle", encodedPassword, "Hồng", "Lê Thị", "Lê Thị Hồng", "https://picsum.photos/400/400?random=72",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u4", "tuan@example.com", "tuanphan", encodedPassword, "Tuấn", "Phan Anh", "Phan Anh Tuấn", "https://picsum.photos/400/400?random=73",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u5", "hoa@example.com", "hoavu", encodedPassword, "Hoa", "Vũ Thị", "Vũ Thị Hoa", "https://picsum.photos/400/400?random=74",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u6", "dat@example.com", "datdo", encodedPassword, "Đạt", "Đỗ Thành", "Đỗ Thành Đạt", "https://picsum.photos/400/400?random=75",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u7", "mai@example.com", "maipham", encodedPassword, "Mai", "Phạm Trúc", "Phạm Trúc Mai", "https://picsum.photos/400/400?random=76",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u8", "hoang@example.com", "hoangngo", encodedPassword, "Hoàng", "Ngô Việt", "Ngô Việt Hoàng", "https://picsum.photos/400/400?random=77",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u9", "thao@example.com", "thaodinh", encodedPassword, "Thảo", "Đinh Phương", "Đinh Phương Thảo", "https://picsum.photos/400/400?random=78",
                    true, true, userRole.getId(), now, now);

            jdbcTemplate.update(insertUserSql,
                    "u10", "khanh@example.com", "khanhtruong", encodedPassword, "Khánh", "Trương Quốc", "Trương Quốc Khánh", "https://picsum.photos/400/400?random=79",
                    true, true, userRole.getId(), now, now);
            
            // Add a test admin
            jdbcTemplate.update(insertUserSql,
                    "admin", "admin@example.com", "admin", encodedPassword, "Admin", "System", "System Admin", "https://picsum.photos/400/400?random=100",
                    true, true, adminRole.getId(), now, now);

            log.info("✅ Mock Users (u1, u2, u3, admin) seeded successfully using JdbcTemplate.");
        } else {
            log.info("✅ Mock Users already exist.");
        }
    }
}
