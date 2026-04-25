package edu.iuh.fit.se.authservice.config;

import edu.iuh.fit.se.authservice.entity.RoleEntity;
import edu.iuh.fit.se.authservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(0)
@RequiredArgsConstructor
public class RoleBootstrap implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        ensureRole("USER", "Regular user");
        ensureRole("ADMIN", "Administrator");
        ensureRole("MODERATOR", "Moderator");
    }

    private void ensureRole(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            Instant now = Instant.now();
            roleRepository.save(RoleEntity.builder()
                    .name(name)
                    .description(description)
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
    }
}
