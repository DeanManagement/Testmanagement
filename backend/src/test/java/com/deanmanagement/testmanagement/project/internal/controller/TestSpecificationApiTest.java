package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM12: the test specification PDF, for the list's filter, a folder subtree or the selected cases. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestSpecificationApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestCaseFolderService folderService;

    private String admin;
    private UUID projectId;
    private UUID shopFolder;
    private TestCaseResponse loose;
    private TestCaseResponse checkout;
    private TestCaseResponse refund;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("sa");
        u.setPasswordHash("x");
        u.setSystemAdmin(true);
        admin = userRepository.save(u).getId().toString();

        Project project = new Project();
        project.setName("Spec Shop");
        project.setKey("SPC");
        projectId = projectRepository.save(project).getId();
        shopFolder = folderService.create(projectId, new CreateTestCaseFolderRequest("Shop", null), null).id();
        UUID payments = folderService.create(projectId, new CreateTestCaseFolderRequest("Payments", shopFolder), null).id();
        refund = create("Refund an order", payments, "Open the order\nPress refund");
        checkout = create("Checkout", shopFolder, "Pay");
        loose = create("Loose ends", null, "Look around");
    }

    @Test
    void theWholeProjectComesInFolderOrderWithEachStep() throws Exception {
        String text = pdfText(get(url()));

        assertThat(text).containsSubsequence("Test Specification", "Loose ends", "Shop", "Checkout",
                "Shop / Payments", "Refund an order", "Open the order", "Press refund");
    }

    @Test
    void aFolderWithItsSubfoldersLeavesTheRestOut() throws Exception {
        String text = pdfText(get(url()).param("folderId", shopFolder.toString()).param("includeSubfolders", "true"));

        assertThat(text).contains("Checkout", "Refund an order").doesNotContain("Loose ends");
    }

    @Test
    void theSelectedCasesOnly() throws Exception {
        String text = pdfText(get(url()).param("ids", loose.id().toString()).param("ids", refund.id().toString()));

        assertThat(text).contains("Loose ends", "Refund an order").doesNotContain("Checkout");
    }

    @Test
    void aViewerOfAnotherProjectIsRefused() throws Exception {
        User outsider = new User();
        outsider.setEmail("o-" + UUID.randomUUID() + "@test.local");
        outsider.setDisplayName("o");
        outsider.setPasswordHash("x");
        String outsiderId = userRepository.save(outsider).getId().toString();

        mockMvc.perform(get(url()).with(user(outsiderId))).andExpect(status().isForbidden());
    }

    private TestCaseResponse create(String title, UUID folderId, String action) {
        return testCaseService.create(projectId, new CreateTestCaseRequest(title, "About " + title, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(), List.of(new TestStepRequest(action, "Done", null)),
                folderId), null);
    }

    private String url() {
        return "/api/projects/" + projectId + "/test-cases/specification";
    }

    private String pdfText(MockHttpServletRequestBuilder request) throws Exception {
        byte[] pdf = mockMvc.perform(request.with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        return text(pdf);
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }
}
