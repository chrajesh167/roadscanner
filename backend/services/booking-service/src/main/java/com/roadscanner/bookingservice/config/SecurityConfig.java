package com.roadscanner.bookingservice.config;

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
 * The security filter chain — this service's own implementation of
 * docs/services/auth-service/security-design.md's "Defense in Depth: Gateway vs. Service",
 * mirroring {@code auth-service}'s own {@code SecurityConfig} structure exactly:
 *
 * <ul>
 *   <li><b>Stateless, bearer-only.</b> No sessions, no CSRF surface — every request is
 *       authenticated by its bearer JWT or not at all.</li>
 *   <li><b>Authentication here, authorization one layer down.</b> This filter chain only
 *       decides "is this a validly-signed, unexpired token" (authentication) and exposes its
 *       {@code role} claim as a {@code ROLE_*} authority. It deliberately does <b>not</b> encode
 *       booking-ownership rules here — those are data-dependent, not role-dependent alone, and
 *       are enforced inside the application layer against {@code Booking.travelerId}
 *       (docs/services/booking-service/boundaries.md's "Booking ↔ Auth"), exactly the
 *       "gateway/service each do their own layer" split {@code authentication-flow.md} requires.</li>
 *   <li><b>Deny by default.</b> Anything not explicitly permitted requires authentication.</li>
 *   <li><b>The internal, service-to-service surface is intentionally unauthenticated in Phase
 *       1</b> — matching {@code inventory-service}'s and {@code provider-integration-service}'s
 *       identical, disclosed {@code /internal/**} gap (relies on the private network boundary
 *       until {@code api-gateway} enforces that path is never routed publicly).</li>
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
                        // Observability + contract surface, per .claude/ARCHITECTURE_RULES.md.
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/health",
                                "/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Service-to-service only — see class Javadoc.
                        .requestMatchers("/internal/**").permitAll()
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
