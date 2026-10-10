package com.spendio.sharedkernel.security;

import com.spendio.config.SecurityProperties;
import jakarta.servlet.DispatcherType;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless security skeleton (ARCHITECTURE.md A.2, E.1): public = the actuator
 * health/info probes, OpenAPI, the auth entry points and GET /currencies; other
 * actuator endpoints (metrics, prometheus) are private; everything under
 * {@code /api/v1} is authenticated; CSRF is enforced only on the two cookie-bearing
 * routes (double submit with the {@code X-XSRF-TOKEN} header).
 *
 * <p>The bearer-token filter authenticates requests, the rate-limit filter guards
 * {@code /auth/**}; both are built here rather than as beans so Boot does not also
 * register them as standalone servlet filters.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_POST_ROUTES = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password"
    };

    private final CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    /**
     * {@code DelegatingPasswordEncoder} with BCrypt cost 12 as the default id and for
     * prefix-less hashes (I.4); the encoder id prefix keeps the Argon2id upgrade path open.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            JwtService jwtService,
            SecurityProperties securityProperties,
            ObjectMapper objectMapper,
            @Value("${spendio.metrics.scrape-token:}") String metricsScrapeToken) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(cookieBearingRoutes()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // C2: only the approved health/info probes are public; every other
                        // operational actuator endpoint requires authentication.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // C2: the private Prometheus scrape is permitted only with the
                        // configured shared bearer token; never exposed via the public edge.
                        .requestMatchers(new MetricsScrapeTokenRequestMatcher(metricsScrapeToken)).permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ROUTES).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/currencies", "/api/v1/currencies/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new AuthRateLimitFilter(securityProperties, objectMapper), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository), CsrfFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Refresh and logout are the only routes carrying a refresh-token cookie. */
    private static RequestMatcher cookieBearingRoutes() {
        return new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"));
    }
}
