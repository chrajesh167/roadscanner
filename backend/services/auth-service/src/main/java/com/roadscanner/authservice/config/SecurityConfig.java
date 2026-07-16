package com.roadscanner.authservice.config;

import com.roadscanner.authservice.adapter.out.security.JwtKeyMaterial;
import com.roadscanner.authservice.adapter.out.security.JwtTokenSignerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * The security filter chain — implementation of docs/services/auth-service/security-design.md
 * for this service's own HTTP surface:
 *
 * <ul>
 *   <li><b>Stateless.</b> No sessions, no CSRF surface: every request is authenticated by its
 *       bearer JWT or not at all. CSRF protection defends cookie-based sessions, which this
 *       service never creates — disabling it is a consequence of statelessness, not a
 *       relaxation.</li>
 *   <li><b>Authentication filter.</b> Spring Security's resource-server support verifies the
 *       bearer token against this service's own RS256 public key — the same verification every
 *       other service performs, per the asymmetric-signing design.</li>
 *   <li><b>RBAC.</b> The token's {@code role} claim maps to a {@code ROLE_*} authority; URL
 *       rules here plus {@code @PreAuthorize} on the role-management endpoint give the
 *       defense-in-depth layering authentication-flow.md requires (a routing mistake is never
 *       the only guard).</li>
 *   <li><b>Deny by default.</b> Anything not explicitly permitted or matched requires
 *       authentication — new endpoints are protected until deliberately opened.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Anonymous by nature: the request body itself carries the credential.
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/**").permitAll()
                        // Observability + contract surface, per .claude/ARCHITECTURE_RULES.md.
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/health",
                                "/actuator/info", "/actuator/metrics/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/logout-all").authenticated()
                        // Internal role elevation — also guarded by @PreAuthorize on the controller.
                        .requestMatchers("/api/v1/auth/roles").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtKeyMaterial keyMaterial, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(JwtTokenSignerAdapter.ROLE_CLAIM);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
