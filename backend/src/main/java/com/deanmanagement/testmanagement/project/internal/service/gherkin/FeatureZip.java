package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The {@code .feature} entries of an uploaded ZIP, by entry name. Everything else in the archive
 * (READMEs, step definitions, macOS metadata) is ignored. Uncompressed size is capped, so a small
 * upload cannot expand into gigabytes.
 */
final class FeatureZip {

    static final long MAX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024;
    private static final String MAC_METADATA_DIR = "__MACOSX/";
    private static final int BUFFER_SIZE = 8192;

    private FeatureZip() {
    }

    /** Sorted by name, so the scenario numbering in the result does not depend on the zip tool. */
    static Map<String, String> read(byte[] zip) {
        Map<String, String> files = new TreeMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().startsWith(MAC_METADATA_DIR)
                        || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".feature")) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("The ZIP expands to more than "
                                + MAX_UNCOMPRESSED_BYTES / (1024 * 1024) + " MB of .feature files");
                    }
                    out.write(buffer, 0, read);
                }
                files.put(entry.getName(), out.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the ZIP: " + e.getMessage());
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("The ZIP contains no .feature files");
        }
        return files;
    }
}
