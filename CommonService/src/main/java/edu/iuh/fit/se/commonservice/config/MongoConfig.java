package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.model.Role;
import edu.iuh.fit.se.commonservice.repository.RoleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MongoConfig {

    private final RoleRepository roleRepository;

    public MongoConfig(@Lazy RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new StringToRoleConverter(roleRepository));
        converters.add(new RoleToStringConverter());
        return new MongoCustomConversions(converters);
    }

    @ReadingConverter
    static class StringToRoleConverter implements Converter<String, Role> {
        private final RoleRepository roleRepository;

        public StringToRoleConverter(@Lazy RoleRepository roleRepository) {
            this.roleRepository = roleRepository;
        }

        @Override
        public Role convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            // Try to find role by ID first
            return roleRepository.findById(source)
                    .orElseGet(() -> {
                        // If not found by ID, try to find by name
                        return roleRepository.findByName(source)
                                .orElse(null);
                    });
        }
    }

    @WritingConverter
    static class RoleToStringConverter implements Converter<Role, String> {
        @Override
        public String convert(Role source) {
            return source != null ? source.getId() : null;
        }
    }
}

