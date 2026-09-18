package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import commits what it reports. Deliberately not {@code @Transactional}: inside a test
 * transaction every write joins it and looks persisted, which is how imports once reported
 * success while writing nothing (they ran in the service's read-only class transaction).
 */
@SpringBootTest
@ActiveProfiles("dev")
class TestCaseImportPersistenceTest {

    @Autowired private TestCaseImportExportService importExportService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private ProjectService projectService;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Import persistence");
        project.setKey("IP" + Integer.toHexString(new java.util.Random().nextInt(0xFFFFF)).toUpperCase());
        projectId = projectRepository.save(project).getId();
    }

    @AfterEach
    void tearDown() {
        projectService.delete(projectId, null);
    }

    private ImportResultResponse importCsv(String csv) {
        return importExportService.importData(projectId, "cases.csv", csv.getBytes(StandardCharsets.UTF_8),
                false, null);
    }

    @Test
    void importedRowsAreCommitted() {
        ImportResultResponse result = importCsv("title,priority\nOne,HIGH\nTwo,LOW\n");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(2);
    }

    @Test
    void aBadRowDoesNotRollBackTheGoodOnes() {
        ImportResultResponse result = importCsv("title,priority\nOne,HIGH\nBad,NOPE\nThree,LOW\n");

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(2);
    }
}
