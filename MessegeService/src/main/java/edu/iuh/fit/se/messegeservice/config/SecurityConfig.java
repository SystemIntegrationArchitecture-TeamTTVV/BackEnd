package edu.iuh.fit.se.messegeservice.config;

import edu.iuh.fit.se.messegeservice.security.JwtAuthenticationFilter;
import edu.iuh.fit.se.messegeservice.security.SwaggerBypassFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SwaggerBypassFilter swaggerBypassFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
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
                // Public endpoints
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/files/**").permitAll() // Allow public access to uploaded files
                .requestMatchers("/billing/payment/vnpay-callback").permitAll() // Allow VNPAY callbacks
                
                // All API endpoints require authentication only (no specific role required)
                .requestMatchers("/conversations/**").authenticated()
                .requestMatchers("/messages/**").authenticated()
                .requestMatchers("/calls/**").authenticated()
                
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(swaggerBypassFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }
        
        return http.build();
    }
}
