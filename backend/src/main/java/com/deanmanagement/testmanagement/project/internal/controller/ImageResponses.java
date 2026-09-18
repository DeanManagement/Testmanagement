package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.service.ImageMediaTypes;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * The response for an image stored in the database (step screenshots, step images, session
 * note images). Security-relevant, so there is one copy (PRD-017, PRD-034 §3.2):
 *
 * <ul>
 *   <li>{@code Cache-Control: private}: the response is authorized per user, so shared caches
 *       (nginx, corporate proxies) must never keep it. The browser cache and ETag/304 keep it fast.</li>
 *   <li>A stored type outside the image allowlist (legacy rows) is served as an
 *       {@code application/octet-stream} attachment, never echoed, or an uploaded text/html
 *       "image" would run script in the app origin. {@code nosniff} and a sandbox CSP back that up.</li>
 *   <li>Fixed length, not streamed: a chunked body cut short is a stream reset over HTTP/2 and 3
 *       ({@code ERR_QUIC_PROTOCOL_ERROR}) rather than a short file.</li>
 * </ul>
 *
 * Callers must have authorized the caller before they read the bytes.
 */
final class ImageResponses {

    private ImageResponses() {
    }

    static ResponseEntity<byte[]> of(Instant updatedAt, String storedContentType, String fileName,
                                     String fallbackFileName, byte[] data, String ifNoneMatch) {
        String etag = "\"" + updatedAt.toEpochMilli() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        boolean safeImageType = ImageMediaTypes.isAllowed(storedContentType);
        MediaType contentType = safeImageType
                ? MediaType.parseMediaType(storedContentType)
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = (safeImageType ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName != null ? fileName : fallbackFileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .eTag(etag)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .contentType(contentType)
                .contentLength(data.length)
                .body(data);
    }
}
