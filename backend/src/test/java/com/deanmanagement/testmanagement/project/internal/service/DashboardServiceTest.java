package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.dashboard.DashboardResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dashboard aggregates with {@code CAST(... AS string)} group-by queries and reads the rows back
 * as Object[]; nothing else in the app exercises those, so a mismatch between what the query returns
 * and what the service casts to only shows up as a 500 at runtime.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;

    @Test
    void getDashboard_withTestCases_groupsByStatusAndPriority() {
        Project project = new Project();
        project.setKey("DSH");
        project.setName("Dashboard");
        project = projectRepository.save(project);

        TestCase testCase = new TestCase();
        testCase.setProject(project);
        testCase.setKey("DSH-1");
        testCase.setTitle("A case");
        testCase.setStatus(TestCaseStatus.ACTIVE);
        testCase.setPriority(Priority.HIGH);
        testCaseRepository.save(testCase);

        DashboardResponse response = dashboardService.getDashboard(project.getId());

        assertThat(response.totals().totalTestCases()).isEqualTo(1);
        assertThat(response.testCasesByStatus()).containsEntry("ACTIVE", 1L);
        assertThat(response.testCasesByPriority()).containsEntry("HIGH", 1L);
    }
}
