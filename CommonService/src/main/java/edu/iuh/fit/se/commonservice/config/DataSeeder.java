package edu.iuh.fit.se.commonservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    @Value("${app.data.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            System.out.println("⚠️ Data seeding is disabled. Set 'app.data.seed.enabled=true' to enable.");
            return;
        }
        
        System.out.println("⚠️ DataSeeder implementation is currently disabled to avoid compilation issues.");
        System.out.println("💡 Re-implement seeding logic when needed.");
    }
}
