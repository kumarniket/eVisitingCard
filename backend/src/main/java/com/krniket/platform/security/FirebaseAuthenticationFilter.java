package com.krniket.platform.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final ObjectProvider<FirebaseAuth> firebaseAuth;
    private final ObjectMapper objectMapper;

    public FirebaseAuthenticationFilter(ObjectProvider<FirebaseAuth> firebaseAuth, ObjectMapper objectMapper) {
        this.firebaseAuth = firebaseAuth;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        FirebaseAuth verifier = firebaseAuth.getIfAvailable();
        if (verifier == null) {
            chain.doFilter(request, response);
            return;
        }

        String verificationStage = "signature_validation";
        try {
            String idToken = authorization.substring(7);
            verifier.verifyIdToken(idToken, false);
            verificationStage = "revocation_check";
            FirebaseToken token = verifier.verifyIdToken(idToken, true);
            Map<String, Object> claims = token.getClaims();
            boolean admin = AdminAccessPolicy.allows(token.getEmail(), claims);
            VerifiedUser user = new VerifiedUser(token.getUid(), token.getEmail(),
                    (String) claims.get("name"), admin);
            var authorities = List.of(new SimpleGrantedAuthority(admin ? "ROLE_SUPER_ADMIN" : "ROLE_VENDOR"));
            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            String traceId = UUID.randomUUID().toString();
            String reason = authenticationFailureReason(exception);
            int upstreamStatus = exception instanceof FirebaseAuthException authException
                    && authException.getHttpResponse() != null
                    ? authException.getHttpResponse().getStatusCode() : 0;
            String diagnostic = exception.getMessage() == null ? ""
                    : exception.getMessage().replaceAll(
                            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[redacted-token]");
            logger.warn("firebase_authentication_failed traceId={} path={} stage={} reason={} upstreamStatus={} diagnostic={}",
                    traceId, request.getRequestURI(), verificationStage, reason, upstreamStatus, diagnostic);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Trace-Id", traceId);
            objectMapper.writeValue(response.getOutputStream(), Map.of("code", "UNAUTHENTICATED",
                    "message", "A valid Firebase ID token is required.", "path", request.getRequestURI(),
                    "traceId", traceId, "reason", reason, "verificationStage", verificationStage,
                    "fields", Map.of()));
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String authorities = authentication == null ? "none" : authentication.getAuthorities().toString();
            logger.info("api_request method={} path={} status={} authorities={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), authorities);
        }
    }

    private String authenticationFailureReason(Exception exception) {
        if (exception instanceof FirebaseAuthException authException) {
            if (authException.getAuthErrorCode() != null) return authException.getAuthErrorCode().name();
            if (authException.getErrorCode() != null) return authException.getErrorCode().name();
        }
        return exception.getClass().getSimpleName();
    }
}
