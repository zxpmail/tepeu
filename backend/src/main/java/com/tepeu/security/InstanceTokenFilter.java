package com.tepeu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 危险宿主 API 要求 {@code X-Tepeu-Token}（或 query {@code token}）。
 * 关联：InstanceTokenService、ApprovalController、FileController。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InstanceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tepeu-Token";

    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/chat/approvals",
            "/api/files/write",
            "/api/files/delete",
            "/api/files/upload",
            "/api/files/restore",
            "/api/files/version"
    );

    private final InstanceTokenService tokens;

    public InstanceTokenFilter(InstanceTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tokens.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        // 令牌本身可从 localhost 拉取，不要求已有令牌
        if (path.startsWith("/api/security/")) {
            return true;
        }
        for (String p : PROTECTED_PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || presented.isBlank()) {
            presented = request.getParameter("token");
        }
        if (tokens.matches(presented)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-Tepeu-Token\",\"data\":null}");
    }
}
