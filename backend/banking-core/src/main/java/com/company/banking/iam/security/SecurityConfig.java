package com.company.banking.iam.security;

import com.company.banking.common.error.ApiErrorWriter;
import com.company.banking.iam.service.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

/**
 * Stateless bearer-token security. URL rules separate the audiences (staff vs platform); fine-grained permissions
 * are enforced with {@code @PreAuthorize} on each endpoint. CSRF protection is unnecessary here because the API
 * never authenticates via cookies; the Next.js BFF that holds cookies applies its own CSRF defence.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private static final String STAFF = ActorJwtAuthenticationConverter.AUDIENCE_AUTHORITY_PREFIX + "staff";
    private static final String PLATFORM = ActorJwtAuthenticationConverter.AUDIENCE_AUTHORITY_PREFIX + "platform";
    private static final Set<String> CREDENTIAL_EXCHANGE_PATHS = Set.of(
            "/api/v1/auth/staff/login", "/api/v1/auth/token/refresh", "/api/v1/auth/mfa/verify",
            "/api/v1/platform/auth/login");

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      JwtDecoder jwtDecoder,
                                                      RestSecurityHandlers handlers,
                                                      SessionService sessionService,
                                                      ApiErrorWriter errorWriter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/staff/login",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/platform/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/platform/**").hasAuthority(PLATFORM)
                        .requestMatchers("/api/v1/**").hasAuthority(STAFF)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new ActorJwtAuthenticationConverter()))
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .addFilterAfter(new AuthenticatedContextFilter(sessionService, errorWriter),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Credential-exchange endpoints ignore any (possibly expired) bearer token the client still sends, so a stale
     * Authorization header can't block login or refresh.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> CREDENTIAL_EXCHANGE_PATHS.contains(request.getRequestURI())
                ? null
                : delegate.resolve(request);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(BankingSecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of()
                : properties.cors().allowedOrigins();
        if (!origins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(origins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                    "X-Correlation-Id", "X-Device-Id"));
            configuration.setExposedHeaders(List.of("X-Correlation-Id"));
            configuration.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }
}
