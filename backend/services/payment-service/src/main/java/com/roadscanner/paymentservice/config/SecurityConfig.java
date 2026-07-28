package com.roadscanner.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * The security filter chain — this service's implementation of
 * docs/services/auth-service/security-design.md's "Defense in Depth: Gateway vs. Service".
 * Stateless, bearer-only; authentication here, ownership authorization one layer down in the
 * application layer. Two non-JWT surfaces are permitted deliberately, each secured differently:
 *
 * <ul>
 *   <li><b>{@code /internal/**}</b> — service-to-service refund surface, unauthenticated in Phase 1
 *       (private-network boundary), the same disclosed posture {@code booking-service} carries.</li>
 *   <li><b>{@code /webhooks/**}</b> — the public gateway webhook endpoint, secured by gateway
 *       <em>signature verification</em> instead of JWT (docs/services/payment-service/boundaries.md's
 *       "Payment &harr; Auth and the Public Webhook Boundary").</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String ROLE_CLAIM = "role";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/health",
                                "/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(ROLE_CLAIM);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
