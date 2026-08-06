package com.deanmanagement.testmanagement.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The security headers that nginx used to add in front of the static app, now that the jar serves
 * it directly.
 *
 * <p>They are scoped to the app shell and must NOT apply to {@code /api/**}. The backend sets its
 * own headers there, and {@code X-Frame-Options: DENY} on the Allure report response would block
 * the sandboxed iframe that renders it (PRD-018). That scoping is the whole reason this is a
 * filter rather than Spring Security's headers DSL, which applies per filter chain rather than per
 * path.
 *
 * <p>{@code X-Frame-Options} is deliberately NOT set here: Spring Security writes its headers when
 * the response commits, which is after this filter runs, so anything it also manages would be
 * silently overwritten. It decides that one per path in {@code UserSecurityConfig} instead.
 *
 * <p>CSP notes: Angular Material injects inline styles, and Chart.js draws to a canvas it exports
 * as {@code data:}/{@code blob:} URLs — as does the authenticated-image pipe.
 */
@Component
public class ShellSecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = "default-src 'self'; img-src 'self' data: blob:; "
            + "style-src 'self' 'unsafe-inline'; font-src 'self' data:; frame-src 'self'; "
            + "object-src 'none'; base-uri 'self'";

    private static final String I18N_PREFIX = "/assets/i18n/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Content-Security-Policy", CSP);

            // Translation catalogues are fetched by filename with no content hash, so a cached
            // copy would survive a release and leave the UI showing the previous wording.
            if (path.startsWith(I18N_PREFIX)) {
                response.setHeader("Cache-Control", "no-cache");
            }
        }

        filterChain.doFilter(request, response);
    }
}
