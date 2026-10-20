package com.krniket.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, FirebaseAuthenticationFilter tokenFilter,
            ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/public/**", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                    .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(objectMapper,
                            request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                            "A valid Firebase ID token is required."))
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            String authorities = authentication == null ? "none"
                                    : authentication.getAuthorities().toString();
                            String traceId = UUID.randomUUID().toString();
                            logger.warn("access_denied traceId={} method={} path={} authorities={}",
                                    traceId, request.getMethod(), request.getRequestURI(), authorities);
                            writeError(objectMapper, request, response, HttpServletResponse.SC_FORBIDDEN,
                                    "FORBIDDEN", "The signed-in account does not have permission for this action.",
                                    traceId);
                        }))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message) throws java.io.IOException {
        writeError(mapper, request, response, status, code, message, UUID.randomUUID().toString());
    }

    private void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message, String traceId) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("X-Trace-Id", traceId);
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message,
                "path", request.getRequestURI(), "traceId", traceId, "fields", Map.of()));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(Arrays.asList("Location", "X-Trace-Id"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
