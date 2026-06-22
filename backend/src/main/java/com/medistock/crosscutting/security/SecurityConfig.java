package com.medistock.crosscutting.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the MediStock REST API.
 *
 * <p>In development, all {@code /api/**} endpoints are publicly accessible.
 * The {@link JwtFilter} extracts the tenant ID from the JWT token when present,
 * but does not enforce authentication.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(final JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // NOSONAR — Stateless JWT API, no session cookie to steal; CSRF protection is inherent in bearer tokens
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
