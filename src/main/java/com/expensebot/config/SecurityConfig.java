package com.expensebot.config;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.accounts.infrastructure.web.UserProvisioningFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ProvisionUserProfileUseCase provisionUserProfile,
                                                   @Value("${cors.allowed-origins}") List<String> allowedOrigins) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // Runs before authentication, so preflights (sent by the browser without a token)
                // and 401 responses also get CORS headers.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOrigins)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Scraped by Prometheus, which sends no JWT. Public in dev; restrict it at deploy time.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Telegram has no JWT; the controller checks its shared secret instead.
                        .requestMatchers("/api/telegram/webhook").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterAfter(new UserProvisioningFilter(provisionUserProfile), BearerTokenAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Lets the SPA, served from another origin, call the API from the browser.
     * The token travels in the Authorization header, not in cookies, so credentials stay disabled.
     * <p>
     * Not a bean on purpose: Spring MVC already registers another {@link CorsConfigurationSource}
     * (mvcHandlerMappingIntrospector), so injecting one by type would only work through a bean-name match.
     */
    private static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // the browser caches a successful preflight for this long, saving one request per call
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
