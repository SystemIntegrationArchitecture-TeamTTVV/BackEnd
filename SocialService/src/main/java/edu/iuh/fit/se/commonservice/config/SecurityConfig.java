package edu.iuh.fit.se.commonservice.config;

import edu.iuh.fit.se.commonservice.security.JwtAuthenticationFilter;
import edu.iuh.fit.se.commonservice.security.SwaggerBypassFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SwaggerBypassFilter swaggerBypassFilter;

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:3000", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"forbidden\",\"message\":\"Access denied\"}");
                })
            );
        
        if (!securityEnabled) {
            // Development mode: Allow all requests without authentication
            http.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        } else {
            // Production mode: Require authentication
            // But allow Swagger requests to bypass (handled by SwaggerBypassFilter)
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/upload/**").permitAll() // Allow upload without role check
                .requestMatchers("/api/files/**").permitAll() // Allow public access to uploaded files
                .requestMatchers("/api/socket/**").permitAll() // Internal cross-service socket emit endpoints
                // WebSocket endpoints - allow all including SockJS info requests
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/ws").permitAll()
                .requestMatchers("/ws/info/**").permitAll()
                .requestMatchers("/ws/info").permitAll()
                
                // Admin-only endpoints - require ADMIN role
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // All API endpoints require authentication (but SwaggerBypassFilter will handle Swagger requests)
                .requestMatchers("/api/groups/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/posts/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/comments/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/friends/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/friend-requests/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/reactions/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/notifications/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/stories/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                .requestMatchers("/api/ai/**").hasAnyRole("ADMIN", "MODERATOR", "USER")
                
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(swaggerBypassFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        
        return http.build();
    }
}
