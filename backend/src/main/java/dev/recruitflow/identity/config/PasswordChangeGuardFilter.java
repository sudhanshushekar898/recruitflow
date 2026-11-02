package dev.recruitflow.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** While a user must change their password, their token only works for the endpoints needed to do so. */
class PasswordChangeGuardFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/change-password", "/api/v1/auth/me", "/api/v1/auth/logout");

    private final ObjectMapper objectMapper;

    PasswordChangeGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaim(PasswordChangeGuardClaim.NAME))
                && !ALLOWED.contains(request.getRequestURI())) {
            ProblemJson.write(response, objectMapper, Problems.of(ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "You must change your password before continuing."), ErrorCode.PASSWORD_CHANGE_REQUIRED.status().value());
            return;
        }
        chain.doFilter(request, response);
    }
}
