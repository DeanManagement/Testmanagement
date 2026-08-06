package com.deanmanagement.testmanagement.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

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

    /**
     * Angular appends an 8-character content hash to every emitted asset — {@code main-3VH7UGOV.js},
     * {@code styles-QE5PISO2.css}, {@code …-A1B2C3D4.woff2}. The hash changes whenever the content
     * does, so these are safe to cache forever; everything else must be revalidated.
     */
    private static final Pattern HASHED_ASSET = Pattern.compile(".*-[A-Z0-9]{8}\\.[a-z0-9]+$");

    private static final String IMMUTABLE = "public, max-age=31536000, immutable";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Content-Security-Policy", CSP);

            // Caching, and the reason this is not left to defaults. Spring serves the shell with
            // a Last-Modified and no Cache-Control, and Spring Security's cache headers are
            // disabled outside /api/**, so nothing forbids caching it. A response with no
            // Cache-Control but a Last-Modified is *heuristically* cacheable: browsers may reuse
            // it for a fraction of its age without asking. That is fatal here, because index.html
            // names its assets by content hash. A stale shell asks for hashes the new jar does
            // not have, every one of them falls through to the SPA fallback and comes back as
            // index.html, and the browser reports a wall of "Refused to apply style … MIME type
            // ('text/html')" with a blank page. The server is fine; the shell is old.
            //
            // So: hashed assets are immutable by construction and cached hard; everything else —
            // the shell, deep links, and the translation catalogues, which are fetched by a plain
            // filename and would otherwise survive a release showing the previous wording — must
            // be revalidated on every request.
            response.setHeader("Cache-Control",
                    HASHED_ASSET.matcher(path).matches() ? IMMUTABLE : "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
