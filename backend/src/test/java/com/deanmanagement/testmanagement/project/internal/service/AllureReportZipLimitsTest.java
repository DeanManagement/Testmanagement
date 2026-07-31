package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.AllureReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** PRD-018: decompression cost must be bounded (zip-bomb / entry-flood protection). */
@ExtendWith(MockitoExtension.class)
class AllureReportZipLimitsTest {

    @Mock
    private AllureReportRepository allureReportRepository;
    @Mock
    private TestRunRepository testRunRepository;

    @InjectMocks
    private AllureReportService service;

    @Test
    void upload_rejectsZipWithTooManyEntries() throws Exception {
        UUID runId = UUID.randomUUID();
        when(testRunRepository.findById(runId)).thenReturn(Optional.of(new TestRun()));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("index.html"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (int i = 0; i <= AllureReportService.MAX_ZIP_ENTRIES; i++) {
                zos.putNextEntry(new ZipEntry("f" + i + ".txt"));
                zos.closeEntry();
            }
        }

        assertThatThrownBy(() -> service.upload(runId, "report.zip", bos.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many entries");
    }

    @Test
    void view_rejectsEntryInflatingPastCap() throws Exception {
        // ~21 MB of zeros compresses to a few KB — a classic high-ratio bomb entry.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("index.html"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("bomb.bin"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 21; i++) {
                zos.write(zeros);
            }
            zos.closeEntry();
        }

        AllureReport report = new AllureReport();
        report.setData(bos.toByteArray());
        when(allureReportRepository.findByTestRunKey("RUN-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getFileFromReport("RUN-1", "bomb.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");

        // The small entry still works.
        service.getFileFromReport("RUN-1", "index.html");
    }

    @Test
    void view_rejectsPathTraversal() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("index.html"));
            zos.write("x".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        AllureReport report = new AllureReport();
        report.setData(bos.toByteArray());
        when(allureReportRepository.findByTestRunKey("RUN-1")).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getFileFromReport("RUN-1", "../outside.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read");
    }
}
