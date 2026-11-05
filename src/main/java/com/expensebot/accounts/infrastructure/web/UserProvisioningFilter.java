package com.expensebot.accounts.infrastructure.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs right after the JWT has been validated and makes sure the caller has a business profile.
 * Requests without a JWT authentication (e.g. public endpoints) pass through untouched.
 * It is built by {@code SecurityConfig} and is deliberately not a bean: a Filter bean would also be
 * registered by the servlet container, outside the security chain and before authentication.
 */
@Slf4j
public class UserProvisioningFilter extends OncePerRequestFilter {
    private final ProvisionUserProfileUseCase provisionUserProfile;

    public UserProvisioningFilter(ProvisionUserProfileUseCase provisionUserProfile) {
        this.provisionUserProfile = provisionUserProfile;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuthentication.getToken();
        Optional<UUID> userId = parseSubject(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        if (userId.isEmpty() || email == null || email.isBlank()) {
            // Never log the values: the email is personal data.
            log.warn("Rejecting token: subject is not a UUID or the email claim is missing");
            response.setHeader("WWW-Authenticate",
                    "Bearer error=\"invalid_token\", error_description=\"subject must be a UUID and email is required\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        provisionUserProfile.execute(userId.get(), email);
        chain.doFilter(request, response);
    }

    // UUID.fromString is lenient (it accepts "1-1-1-1-1"), so the canonical form is checked too.
    private static Optional<UUID> parseSubject(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(subject);
            return uuid.toString().equalsIgnoreCase(subject) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
