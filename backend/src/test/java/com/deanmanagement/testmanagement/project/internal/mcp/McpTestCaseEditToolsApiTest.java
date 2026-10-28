package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.StepImageRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.AttachmentService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM09 and TM06: copying a case, and changing one step without replacing the others. */
class McpTestCaseEditToolsApiTest extends McpToolApiTestSupport {

    @Autowired private TestCaseEditTools editTools;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private StepImageRepository stepImageRepository;
    @Autowired private AttachmentService attachmentService;
    @Autowired private TestCaseService testCaseService;

    private void attachImage(UUID testCaseId, int stepIndex, String fileName) {
        TestStep step = testCaseRepository.findByIdWithSteps(testCaseId).orElseThrow().getSteps().get(stepIndex);
        StepImage image = new StepImage();
        image.setTestStep(step);
        image.setFileName(fileName);
        image.setContentType("image/png");
        image.setData(new byte[] {1, 2, 3});
        stepImageRepository.save(image);
    }

    /** Each step's action with the file name of its image, or "-" when it has none. */
    private List<String> stepsWithImages(UUID testCaseId) {
        return testCaseRepository.findByIdWithSteps(testCaseId).orElseThrow().getSteps().stream()
                .map(s -> s.getAction() + ":" + (s.getImage() == null ? "-" : s.getImage().getFileName()))
                .toList();
    }

    // ---- copy_test_case (TM09) ----------------------------------------------------------------

    @Test
    void copyIsANewDraftWithTheStepsImagesAndAttachments() {
        UUID userId = authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase original = createCase("Checkout", "Open basket", "Pay");
        attachImage(original.id(), 1, "pay.png");
        attachmentService.upload(project.getId(), original.id(), "notes.txt", "text/plain", "hi".getBytes(), userId);

        McpDtos.CreatedTestCase copy = editTools.copyTestCase(original.key(), null);

        assertThat(copy.key()).isNotEqualTo(original.key());
        assertThat(copy.title()).isEqualTo("Checkout (copy)");
        assertThat(copy.status()).isEqualTo(TestCaseStatus.DRAFT);
        assertThat(stepsWithImages(copy.id())).containsExactly("Open basket:-", "Pay:pay.png");
        assertThat(testCaseTools.getTestCase(copy.key()).attachments())
                .extracting(McpDtos.Attachment::fileName).containsExactly("notes.txt");
    }

    @Test
    void copyTakesTheTitleItIsGiven() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase original = createCase("Checkout", "Pay");

        assertThat(editTools.copyTestCase(original.key(), "Checkout with voucher").title())
                .isEqualTo("Checkout with voucher");
    }

    @Test
    void copyIsRefusedToAReadOnlyKey() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase original = createCase("Checkout", "Pay");
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> editTools.copyTestCase(original.key(), null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void aCaseOfAnotherProjectCannotBeCopied() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Theirs", "Step");
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> editTools.copyTestCase(foreign.id().toString(), null))
                .isInstanceOf(RuntimeException.class);
    }

    // ---- one step at a time (TM06) -------------------------------------------------------------

    /** A, B, C with an image on B and on C. */
    private McpDtos.CreatedTestCase caseWithImages() {
        McpDtos.CreatedTestCase created = createCase("Checkout", "A", "B", "C");
        attachImage(created.id(), 1, "b.png");
        attachImage(created.id(), 2, "c.png");
        return created;
    }

    @Test
    void insertingAStepLeavesTheOtherStepsAndTheirImagesAlone() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();

        editTools.insertTestStep(created.key(), 2, "New", "ok", null, null);

        assertThat(stepsWithImages(created.id())).containsExactly("A:-", "New:-", "B:b.png", "C:c.png");
    }

    @Test
    void insertingWithoutAPositionAppends() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();

        editTools.insertTestStep(created.key(), null, "Last", null, null, null);

        assertThat(stepsWithImages(created.id())).last().isEqualTo("Last:-");
    }

    @Test
    void aMovedStepTakesItsImageWithIt() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();

        editTools.moveTestStep(created.key(), 3, 1);

        assertThat(stepsWithImages(created.id())).containsExactly("C:c.png", "A:-", "B:b.png");
    }

    @Test
    void removingAStepKeepsTheImagesOfTheStepsAfterIt() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();

        editTools.removeTestStep(created.key(), 2);

        assertThat(stepsWithImages(created.id())).containsExactly("A:-", "C:c.png");
    }

    @Test
    void aPositionOutsideTheStepsIsRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();

        assertThatThrownBy(() -> editTools.removeTestStep(created.key(), 4))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("1-3");
        assertThat(stepsWithImages(created.id())).hasSize(3);
    }

    @Test
    void aFullUpdateThatNamesStepIdsKeepsEachImageWithItsStep() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWithImages();
        List<UUID> ids = testCaseRepository.findByIdWithSteps(created.id()).orElseThrow().getSteps().stream()
                .map(TestStep::getId).toList();

        // Reordered to C, A and B removed: by position B's image would land on A, and C's would be lost.
        testCaseService.update(project.getId(), created.id(), new UpdateTestCaseRequest(null, null, null, null,
                null, null, List.of(new TestStepRequest("C", null, null, null, ids.get(2)),
                        new TestStepRequest("A", null, null, null, ids.get(0))), null, null), null);

        assertThat(stepsWithImages(created.id())).containsExactly("C:c.png", "A:-");
    }
}
