package dev.recruitflow.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.error.Problems;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Stateless, deny-by-default API security. Role rules here are the first of three layers (see ARCHITECTURE.md 6.2). */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    private static final String AUTH = "/api/v1/auth/";
    private static final java.util.Set<String> KNOWN_ROLES = java.util.Arrays.stream(Role.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** BCrypt cost 12, wrapped so hashes carry an id and can be upgraded to another algorithm later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            ProblemJson.write(response, mapper, Problems.of(ErrorCode.UNAUTHENTICATED, "Authentication required."),
                    ErrorCode.UNAUTHENTICATED.status().value());
        };
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, ex) -> ProblemJson.write(response, mapper,
                Problems.of(ErrorCode.FORBIDDEN, "You do not have permission to perform this action."),
                ErrorCode.FORBIDDEN.status().value());
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream()
                            .filter(KNOWN_ROLES::contains)
                            .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .toList();
            return authorities;
        });
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                            AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler,
                                            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // bearer-token API, no cookie-authenticated endpoints
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                AUTH + "login", AUTH + "refresh", AUTH + "logout",
                                AUTH + "accept-invite", AUTH + "forgot-password", AUTH + "reset-password").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        // Interviewers get scoped candidate access in M6, through the interview module.
                        .requestMatchers("/api/v1/candidates/**", "/api/v1/clients/**", "/api/v1/jobs/**",
                                "/api/v1/applications/**").hasAnyRole("ADMIN", "RECRUITER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .addFilterAfter(new PasswordChangeGuardFilter(objectMapper), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
