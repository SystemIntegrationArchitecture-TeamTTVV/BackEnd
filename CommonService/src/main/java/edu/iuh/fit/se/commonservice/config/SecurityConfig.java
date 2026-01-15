package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // Admin only endpoints
                .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/posts/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/comments/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/groups/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/friends/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/friend-requests/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/reactions/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/notifications/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/stories/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
