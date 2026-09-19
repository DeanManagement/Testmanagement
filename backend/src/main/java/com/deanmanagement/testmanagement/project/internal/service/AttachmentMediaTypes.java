package com.deanmanagement.testmanagement.project.internal.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What an attachment really is (PRD-044 §3.2). The declared type is only a claim: it must be on the
 * allowlist and the file's first bytes must match it, so a page of HTML cannot arrive calling itself
 * a PDF. Only raster images are ever shown inline; everything else downloads.
 */
public final class AttachmentMediaTypes {

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int TEXT_SNIFF_BYTES = 8 * 1024;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = {'G', 'I', 'F', '8'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};

    private enum Kind { PNG, JPEG, GIF, WEBP, PDF, ZIP, TEXT }

    private static final Map<String, Kind> ALLOWED = Map.ofEntries(
            Map.entry("image/png", Kind.PNG),
            Map.entry("image/jpeg", Kind.JPEG),
            Map.entry("image/gif", Kind.GIF),
            Map.entry("image/webp", Kind.WEBP),
            Map.entry("application/pdf", Kind.PDF),
            Map.entry("application/zip", Kind.ZIP),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Kind.ZIP),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Kind.ZIP),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Kind.ZIP),
            Map.entry("text/plain", Kind.TEXT),
            Map.entry("text/csv", Kind.TEXT),
            Map.entry("application/json", Kind.TEXT),
            Map.entry("application/xml", Kind.TEXT),
            Map.entry("text/xml", Kind.TEXT));

    /** Types a browser would run script from; refused even if they otherwise looked fine. */
    private static final Set<String> NEVER = Set.of("image/svg+xml", "text/html", "application/xhtml+xml",
            "text/javascript", "application/javascript");

    /**
     * Browsers send octet-stream for files they do not recognise, CSV and JSON often among them.
     * Only then is the extension consulted, and it maps to download-only types: an extension can never
     * make a file an inline image.
     */
    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"));

    private AttachmentMediaTypes() {
    }

    /**
     * The verified media type to store.
     *
     * @throws IllegalArgumentException when the type is not allowed or the bytes do not match it
     */
    public static String verify(String declaredType, String fileName, byte[] data) {
        String type = declaredType == null ? "" : normalize(declaredType);
        if (type.isEmpty() || type.equals(OCTET_STREAM)) {
            type = BY_EXTENSION.getOrDefault(extension(fileName), OCTET_STREAM);
        }
        if (NEVER.contains(type)) {
            throw new IllegalArgumentException("Files of type " + type + " cannot be attached: a browser could run them");
        }
        Kind kind = ALLOWED.get(type);
        if (kind == null) {
            throw new IllegalArgumentException("Unsupported file type '" + type + "'. Allowed: images (PNG, JPEG, GIF, "
                    + "WebP), PDF, ZIP and Office documents, and text files (TXT, CSV, JSON, XML)");
        }
        if (!matches(kind, data)) {
            throw new IllegalArgumentException("The file's content does not match its type " + type);
        }
        return type;
    }

    private static boolean matches(Kind kind, byte[] data) {
        return switch (kind) {
            case PNG -> startsWith(data, PNG);
            case JPEG -> startsWith(data, JPEG);
            case GIF -> startsWith(data, GIF);
            case WEBP -> data.length >= 12 && startsWith(data, new byte[]{'R', 'I', 'F', 'F'})
                    && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case PDF -> startsWith(data, PDF);
            case ZIP -> startsWith(data, ZIP);
            case TEXT -> isText(data);
        };
    }

    /** Binary content has NUL bytes early on; text in any common encoding but UTF-16 does not. */
    private static boolean isText(byte[] data) {
        int end = Math.min(data.length, TEXT_SNIFF_BYTES);
        for (int i = 0; i < end; i++) {
            if (data[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String contentType) {
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }
}
