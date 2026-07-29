package com.roadscanner.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * The security filter chain, mirroring {@code booking-service}'s and {@code payment-service}'s
 * structure: stateless, bearer-only, with the {@code role} claim exposed as a {@code ROLE_*}
 * authority.
 *
 * <p>What differs here is the default posture, because this service is different in kind. Search
 * is a <em>public read</em> service: anonymous travellers query trips and locations without ever
 * holding a token, and that must keep working exactly as before. So the anonymous surface is
 * enumerated explicitly and permitted, rather than the whole service defaulting to
 * authenticated.
 *
 * <p>The one authenticated island is the location catalogue's write side. Those three routes are
 * administrative — they author the canonical location data the rest of the platform resolves
 * against — so they require {@code ROLE_ADMIN} at the service itself, not only at
 * {@code api-gateway}. Defence in depth: a misrouted gateway rule, or anything that reaches this
 * service on the private network, must still fail to write.
 *
 * <p>Rule order matters — Spring Security takes the first match — so the write matchers are
 * declared before the {@code /api/v1/locations} read matchers they would otherwise be shadowed
 * by.
 *
 * <p>Anything not listed falls through to {@code authenticated()}. That is deliberate: a new
 * endpoint added later is closed until someone decides it should be open, rather than silently
 * inheriting anonymous access.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The claim auth-service puts the role in — see that service's {@code JwtTokenSignerAdapter}. */
    public static final String ROLE_CLAIM = "role";

    /** The role permitted to author the location catalogue. */
    public static final String ADMIN_ROLE = "ADMIN";

    private static final String LOCATIONS = "/api/v1/locations";
    private static final String LOCATION_BY_ID = "/api/v1/locations/*";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // --- Administrative: authoring the canonical location catalogue. ---
                        .requestMatchers(HttpMethod.POST, LOCATIONS).hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PUT, LOCATION_BY_ID).hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, LOCATION_BY_ID).hasRole(ADMIN_ROLE)

                        // --- Public read surface: unchanged, anonymous, exactly as before. ---
                        .requestMatchers(HttpMethod.GET, LOCATIONS, LOCATION_BY_ID).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()
                        // Place autocomplete: a traveller typing a destination is not logged in.
                        // It proxies Google server-side purely so the API key never reaches the
                        // browser — it writes nothing, so there is nothing here to authorize.
                        .requestMatchers(HttpMethod.GET, "/api/v1/google/places").permitAll()

                        // Observability + contract surface, per .claude/ARCHITECTURE_RULES.md.
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/health",
                                "/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Service-to-service operational surface. Left unauthenticated to match
                        // inventory-service's and booking-service's identical, disclosed
                        // /internal/** gap — it relies on the private network boundary until
                        // api-gateway guarantees the path is never routed publicly.
                        .requestMatchers("/internal/**").permitAll()

                        // Lets the container's error dispatch render a body instead of turning
                        // every handled failure into an opaque 403.
                        .requestMatchers("/error").permitAll()

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
