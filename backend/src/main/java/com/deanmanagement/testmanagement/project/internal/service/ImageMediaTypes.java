package com.deanmanagement.testmanagement.project.internal.service;

import java.util.Locale;
import java.util.Set;

/**
 * Allowlist for uploaded image media types. Uploads declaring anything else are rejected,
 * and downloads never echo a stored type outside this set — otherwise a "screenshot"
 * uploaded as text/html or image/svg+xml would execute script in the app origin when
 * viewed (stored XSS).
 */
public final class ImageMediaTypes {

    private static final Set<String> ALLOWED = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp"
    );

    private ImageMediaTypes() {
    }

    public static boolean isAllowed(String contentType) {
        return contentType != null && ALLOWED.contains(normalize(contentType));
    }

    /** Returns the normalized media type, or throws if it is not an allowed image type. */
    public static String requireAllowed(String contentType) {
        if (!isAllowed(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported image content type '" + contentType + "'. Allowed: " + ALLOWED);
        }
        return normalize(contentType);
    }

    /** Strips media-type parameters (e.g. "; charset=...") and lowercases. */
    private static String normalize(String contentType) {
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
