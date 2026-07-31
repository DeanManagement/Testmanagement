package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.AllureReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllureReportService {

    private final AllureReportRepository allureReportRepository;
    private final TestRunRepository testRunRepository;

    /** PRD-018: bound decompression cost — a high-ratio zip must not inflate unbounded. */
    static final long MAX_ENTRY_DECOMPRESSED_BYTES = 20L * 1024 * 1024; // 20 MB per entry
    static final int MAX_ZIP_ENTRIES = 5_000;

    private static final Map<String, String> CONTENT_TYPE_MAP = Map.ofEntries(
            Map.entry("html", "text/html"),
            Map.entry("js", "application/javascript"),
            Map.entry("css", "text/css"),
            Map.entry("json", "application/json"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            Map.entry("csv", "text/csv"),
            Map.entry("xml", "application/xml"),
            Map.entry("txt", "text/plain")
    );

    @Transactional
    public AllureReport upload(UUID testRunId, String fileName, byte[] data) {
        TestRun testRun = testRunRepository.findById(testRunId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunId));

        validateZipContainsIndexHtml(data);

        allureReportRepository.findByTestRunId(testRunId)
                .ifPresent(allureReportRepository::delete);

        AllureReport report = new AllureReport();
        report.setTestRun(testRun);
        report.setFileName(fileName);
        report.setData(data);

        return allureReportRepository.save(report);
    }

    public AllureReportFile getFileFromReport(String testRunKey, String filePath) {
        AllureReport report = allureReportRepository.findByTestRunKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("AllureReport", testRunKey));

        String normalizedPath = normalizeFilePath(filePath);
        Path tempFile = writeTempZip(report.getData());

        try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
            String basePath = detectBasePath(zipFile);
            String fullEntryName = basePath + normalizedPath;

            // Validate path traversal: ensure resolved path stays within base path.
            // Note: when index.html sits at the zip root, basePath is "" and
            // Path("x").startsWith(Path("")) is false — handle that case explicitly.
            Path basePathObj = Path.of(basePath);
            Path resolvedPath = basePathObj.resolve(normalizedPath).normalize();
            boolean escapes = resolvedPath.isAbsolute()
                    || (basePath.isEmpty()
                            ? resolvedPath.startsWith("..")
                            : !resolvedPath.startsWith(basePathObj));
            if (escapes) {
                throw new IOException("Zip entry is outside of the target dir: " + fullEntryName);
            }

            ZipEntry entry = zipFile.getEntry(fullEntryName);
            if (entry != null && !entry.isDirectory()) {
                byte[] content = readBounded(zipFile, entry);
                String contentType = detectContentType(normalizedPath);
                return new AllureReportFile(content, contentType);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read allure report ZIP", e);
        } finally {
            deleteTempFile(tempFile);
        }

        throw new ResourceNotFoundException("File in AllureReport", filePath);
    }

    @Transactional
    public void deleteByTestRunId(UUID testRunId) {
        allureReportRepository.findByTestRunId(testRunId)
                .ifPresent(allureReportRepository::delete);
    }

    private void validateZipContainsIndexHtml(byte[] data) {
        Path tempFile = writeTempZip(data);
        try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
            boolean hasIndex = false;
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException(
                            "ZIP contains too many entries (max " + MAX_ZIP_ENTRIES + ")");
                }
                if (entry.getName().endsWith("index.html")) {
                    hasIndex = true;
                }
            }
            if (!hasIndex) {
                throw new IllegalArgumentException("ZIP must contain an index.html file");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid ZIP file", e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * Reads a zip entry with a hard decompressed-size cap (PRD-018). The entry's declared
     * size cannot be trusted, so the stream itself is bounded while copying.
     */
    private byte[] readBounded(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream is = zipFile.getInputStream(entry);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ENTRY_DECOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("Report file too large (max "
                            + (MAX_ENTRY_DECOMPRESSED_BYTES / (1024 * 1024)) + " MB per entry)");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String detectBasePath(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith("/index.html") || name.equals("index.html")) {
                int lastSlash = name.lastIndexOf('/');
                return lastSlash == -1 ? "" : name.substring(0, lastSlash + 1);
            }
        }
        return "";
    }

    private String normalizeFilePath(String filePath) {
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        return filePath;
    }

    private Path writeTempZip(byte[] data) {
        try {
            Path tempFile = Files.createTempFile("allure-report-", ".zip");
            Files.write(tempFile, data);
            return tempFile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create temp file for ZIP", e);
        }
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file {}: {}", path, e.getMessage());
        }
    }

    private String detectContentType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return "application/octet-stream";
        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        return CONTENT_TYPE_MAP.getOrDefault(extension, "application/octet-stream");
    }

    public record AllureReportFile(byte[] content, String contentType) {}
}
