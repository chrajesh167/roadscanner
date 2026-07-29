package com.roadscanner.providerintegrationservice.config;

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
 * The security filter chain, identical in shape to {@code search-service}'s,
 * {@code booking-service}'s and {@code payment-service}'s: stateless, bearer-only, with the
 * {@code role} claim exposed as a {@code ROLE_*} authority.
 *
 * <p>Sprint 2 introduced the first human-facing surface in this service. Everything here was
 * previously {@code /internal/**}, called service-to-service on a private network; the new
 * {@code /api/v1/providers} routes author the platform's provider registry — which providers
 * exist, which are enabled, and what credentials they authenticate with — so they require
 * {@code ROLE_ADMIN} at the service itself rather than trusting {@code api-gateway} alone.
 *
 * <p>{@code /internal/**} remains unauthenticated, matching the disclosed posture it has always
 * had (see {@code service-boundaries.md}). Changing that is a separate decision affecting every
 * caller in the platform, not something to slip into this sprint.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The claim auth-service puts the role in — see that service's {@code JwtTokenSignerAdapter}. */
    public static final String ROLE_CLAIM = "role";

    /** The role permitted to administer the provider registry. */
    public static final String ADMIN_ROLE = "ADMIN";

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

                        // Pre-existing service-to-service surface — unchanged, see class Javadoc.
                        .requestMatchers("/internal/**").permitAll()

                        // Lets the container's error dispatch render a body instead of turning
                        // every handled failure into an opaque 403.
                        .requestMatchers("/error").permitAll()

                        // The provider registry: authoring it is an administrative act.
                        .requestMatchers("/api/v1/providers/**").hasRole(ADMIN_ROLE)

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
