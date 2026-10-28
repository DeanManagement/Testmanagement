package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.AllureReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second upload to the same run failed with a 500 (TM38): the unique run column broke when the old
 * report was deleted and the new one inserted in one flush. Not {@code @Transactional}, because the
 * failure only showed at commit.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AllureReportReplaceTest {

    @Autowired
    private AllureReportService allureReportService;
    @Autowired
    private AllureReportRepository allureReportRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestRunRepository testRunRepository;

    @Test
    void aSecondUploadReplacesTheRunsReport() throws Exception {
        Project project = new Project();
        project.setName("Allure ersetzt");
        project.setKey("ALR" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)));
        project = projectRepository.save(project);
        TestRun run = new TestRun();
        run.setProject(project);
        run.setKey(project.getKey() + "-R1");
        run.setName("run");
        run.setStatus(TestRunStatus.IN_PROGRESS);
        UUID runId = testRunRepository.save(run).getId();
        allureReportService.upload(runId, "first.zip", zipWithIndex("first"));

        allureReportService.upload(runId, "second.zip", zipWithIndex("second"));

        assertThat(allureReportRepository.findByTestRunId(runId).orElseThrow().getFileName())
                .isEqualTo("second.zip");
    }

    private static byte[] zipWithIndex(String body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("index.html"));
            zip.write(body.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
