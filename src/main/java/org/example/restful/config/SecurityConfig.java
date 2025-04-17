package org.example.restful.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/**").permitAll() // Autoriser les endpoints d'authentification
                .anyRequest().authenticated()               // Authentification requise pour les autres endpoints
            )
            .csrf(csrf -> csrf.disable()); // Désactiver CSRF si nécessaire
        return http.build();
    }
}
