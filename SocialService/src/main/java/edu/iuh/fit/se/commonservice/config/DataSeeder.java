package edu.iuh.fit.se.commonservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Demo data seeding previously created Mongo users here. Users/roles now live in {@code AuthService} (PostgreSQL).
 * Use AuthService register API or SQL seed for accounts; enable Mongo seed scripts separately if needed.
 */
@Configuration
@Slf4j
public class DataSeeder implements CommandLineRunner {

    @Value("${app.data.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (seedEnabled) {
            log.warn("app.data.seed.enabled=true but Mongo user seeding was removed. Create users via AuthService.");
        }
    }
}
