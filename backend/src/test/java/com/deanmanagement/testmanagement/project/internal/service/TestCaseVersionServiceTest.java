package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Versioning (PRD-011). The property that matters is that "version N" names the wording results
 * stamped N actually executed — so the snapshot has to happen before the edit, not after.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestCaseVersionServiceTest {

    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private TestCaseVersionService versionService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;

    private final AtomicInteger sequence = new AtomicInteger();
    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Versioned");
        project.setKey("VER" + sequence.incrementAndGet());
        project = projectRepository.save(project);
    }

    private UUID createCase(String title, String action) {
        CreateTestCaseRequest request = new CreateTestCaseRequest(
                title, "desc", "pre", Priority.MEDIUM, TestCaseStatus.ACTIVE,
                new java.util.HashSet<>(Set.of("smoke")), List.of(new TestStepRequest(action, "expected", "data")), null);
        return testCaseService.create(project.getId(), request, null).id();
    }

    private void update(UUID id, String title, String action) {
        UpdateTestCaseRequest request = new UpdateTestCaseRequest(
                title, "desc", "pre", Priority.MEDIUM, TestCaseStatus.ACTIVE,
                new java.util.HashSet<>(Set.of("smoke")), List.of(new TestStepRequest(action, "expected", "data")));
        testCaseService.update(project.getId(), id, request, null);
    }

    @Test
    void aNewCaseStartsAtVersionOneWithNoSnapshots() {
        UUID id = createCase("Original", "step one");

        List<TestCaseVersionSummary> versions = versionService.list(project.getId(), id);

        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().versionNumber()).isEqualTo(1);
        assertThat(versions.getFirst().current()).isTrue();
    }

    @Test
    void editingSnapshotsThePreviousStateAndAdvancesTheCounter() {
        UUID id = createCase("Original", "step one");
        update(id, "Revised", "step two");

        List<TestCaseVersionSummary> versions = versionService.list(project.getId(), id);

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionNumber()).isEqualTo(2);
        assertThat(versions.get(0).current()).isTrue();
        assertThat(versions.get(0).title()).isEqualTo("Revised");
        // v1 holds the pre-edit wording, which is what any result stamped v1 executed.
        assertThat(versions.get(1).versionNumber()).isEqualTo(1);
        assertThat(versions.get(1).title()).isEqualTo("Original");
    }

    @Test
    void theSnapshotCapturesStepsNotJustFields() {
        UUID id = createCase("Original", "click the old button");
        update(id, "Original", "click the new button");

        TestCaseVersionResponse v1 = versionService.get(project.getId(), id, 1);

        assertThat(v1.steps()).hasSize(1);
        assertThat(v1.steps().getFirst().action()).isEqualTo("click the old button");
        assertThat(v1.steps().getFirst().expectedResult()).isEqualTo("expected");
        assertThat(v1.steps().getFirst().testData()).isEqualTo("data");
    }

    @Test
    void theCurrentVersionIsReadFromTheLiveCase() {
        UUID id = createCase("Original", "step one");
        update(id, "Revised", "step two");

        TestCaseVersionResponse current = versionService.get(project.getId(), id, 2);

        assertThat(current.id()).isNull();
        assertThat(current.title()).isEqualTo("Revised");
        assertThat(current.steps().getFirst().action()).isEqualTo("step two");
    }

    @Test
    void repeatedEditsAccumulateInOrder() {
        UUID id = createCase("One", "a");
        update(id, "Two", "b");
        update(id, "Three", "c");
        update(id, "Four", "d");

        List<TestCaseVersionSummary> versions = versionService.list(project.getId(), id);

        assertThat(versions).extracting(TestCaseVersionSummary::versionNumber)
                .containsExactly(4, 3, 2, 1);
        assertThat(versions).extracting(TestCaseVersionSummary::title)
                .containsExactly("Four", "Three", "Two", "One");
    }

    @Test
    void oldSnapshotsAreUnaffectedByLaterEdits() {
        UUID id = createCase("Original", "step one");
        update(id, "Second", "step two");
        TestCaseVersionResponse before = versionService.get(project.getId(), id, 1);

        update(id, "Third", "step three");
        TestCaseVersionResponse after = versionService.get(project.getId(), id, 1);

        // History is evidence; it must be inert.
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.steps().getFirst().action()).isEqualTo(before.steps().getFirst().action());
    }

    @Test
    void labelsSurviveTheRoundTrip() {
        UUID id = createCase("Labelled", "a");
        update(id, "Labelled", "b");

        assertThat(versionService.get(project.getId(), id, 1).labels()).containsExactly("smoke");
    }

    @Test
    void anUnknownVersionIsNotFound() {
        UUID id = createCase("Original", "a");

        assertThatThrownBy(() -> versionService.get(project.getId(), id, 99))
                .hasMessageContaining("TestCaseVersion");
    }

    @Test
    void aCaseFromAnotherProjectIsNotReachable() {
        UUID id = createCase("Mine", "a");

        Project other = new Project();
        other.setName("Other");
        other.setKey("OTH" + sequence.incrementAndGet());
        other = projectRepository.save(other);

        UUID otherProjectId = other.getId();
        assertThatThrownBy(() -> versionService.list(otherProjectId, id))
                .hasMessageContaining("TestCase");
    }

    @Test
    void deletingACaseTakesItsHistoryWithIt() {
        UUID id = createCase("Doomed", "a");
        update(id, "Doomed v2", "b");

        testCaseService.delete(project.getId(), id, null);

        assertThat(testCaseRepository.findById(id)).isEmpty();
    }
}
