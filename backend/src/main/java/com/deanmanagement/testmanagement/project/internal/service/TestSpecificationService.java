package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.spec.TestCaseSpecifications;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The test specification PDF (TM12, PRD-053 §4): the selected cases, or those the list filter
 * matches (a folder with its subfolders included), in folder-tree order and by key within a folder.
 */
@Service
@Transactional(readOnly = true)
public class TestSpecificationService {

    /** Beyond this the export is refused: narrow the filter or the folder instead. */
    static final int MAX_CASES = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String SPEC_CSS = """
            h2.folder { border-bottom: 1px solid #ddd; padding-bottom: 2px; }
            h3 { font-size: 13px; margin: 18px 0 6px 0; }
            h4 { font-size: 11px; margin: 10px 0 4px 0; color: #555; }
            p.text { margin: 0 0 6px 0; }
            div.case { page-break-inside: auto; }
            table.summary { width: auto; margin-bottom: 8px; }
            table.spec-steps tr { page-break-inside: avoid; }
            table.spec-steps td { vertical-align: top; }
            img.step-image { max-width: 260px; max-height: 200px; margin-top: 4px; }
            .shared { color: #1976d2; font-size: 9px; }
            """;

    private final TestCaseRepository testCaseRepository;
    private final TestCaseFolderRepository folderRepository;
    private final ProjectRepository projectRepository;
    private final AttachmentService attachmentService;
    private final StepAttachmentService stepAttachmentService;
    private final long imageBudgetBytes;

    public TestSpecificationService(TestCaseRepository testCaseRepository, TestCaseFolderRepository folderRepository,
                                    ProjectRepository projectRepository, AttachmentService attachmentService,
                                    StepAttachmentService stepAttachmentService,
                                    @Value("${app.spec-export.max-image-bytes:20971520}") long imageBudgetBytes) {
        this.testCaseRepository = testCaseRepository;
        this.folderRepository = folderRepository;
        this.projectRepository = projectRepository;
        this.attachmentService = attachmentService;
        this.stepAttachmentService = stepAttachmentService;
        this.imageBudgetBytes = imageBudgetBytes;
    }

    /** {@code ids}, when given, wins over {@code filter}: the cases selected in the list. */
    public byte[] generate(UUID projectId, TestCaseListFilter filter, Collection<UUID> ids) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        List<TestCase> cases = ids == null || ids.isEmpty()
                ? testCaseRepository.findAll(TestCaseSpecifications.build(projectId, filter, folderIds(projectId, filter)))
                : testCaseRepository.findByIdInAndProjectId(ids, projectId);
        if (cases.size() > MAX_CASES) {
            throw new IllegalArgumentException("The specification would hold " + cases.size() + " test cases; at most "
                    + MAX_CASES + " fit in one export. Narrow the filter or pick a folder.");
        }
        List<TestCaseFolder> folders = folderRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<UUID, Integer> treeOrder = new HashMap<>();
        Map<UUID, String> paths = new HashMap<>();
        walk(folders, null, "", treeOrder, paths);
        List<TestCase> ordered = cases.stream().sorted(inFolderOrder(treeOrder)).toList();

        // ponytail: steps and images load per case (lazy); fine up to MAX_CASES, fetch-join if it gets slow.
        List<UUID> stepIds = ordered.stream()
                .flatMap(tc -> StepExpansion.expandedSteps(tc.getSteps()).stream())
                .map(TestStep::getId).distinct().toList();
        String html = new TestSpecHtml(ordered, paths,
                attachmentService.summariesByTestCase(ordered.stream().map(TestCase::getId).toList()),
                stepAttachmentService.summariesByTestStep(stepIds), imageBudgetBytes, DATE_FMT)
                .build(project.getName(), PdfReportService.CSS + SPEC_CSS);
        return PdfReportService.renderPdf(html);
    }

    private Set<UUID> folderIds(UUID projectId, TestCaseListFilter filter) {
        if (filter.folderId() == null) {
            return null;
        }
        return filter.includeSubfolders() ? folderRepository.findSubtreeIds(projectId, filter.folderId())
                : Set.of(filter.folderId());
    }

    /** Depth first, siblings by their sort order: the order of the folder tree in the app. */
    private static void walk(List<TestCaseFolder> folders, UUID parentId, String prefix, Map<UUID, Integer> order,
                             Map<UUID, String> paths) {
        for (TestCaseFolder folder : folders) {
            UUID folderParent = folder.getParent() == null ? null : folder.getParent().getId();
            if (Objects.equals(folderParent, parentId)) {
                String path = prefix.isEmpty() ? folder.getName() : prefix + " / " + folder.getName();
                order.put(folder.getId(), order.size());
                paths.put(folder.getId(), path);
                walk(folders, folder.getId(), path, order, paths);
            }
        }
    }

    /** Cases without a folder first, then by folder; within one, by key number (PROJ-2 before PROJ-10). */
    private static Comparator<TestCase> inFolderOrder(Map<UUID, Integer> treeOrder) {
        return Comparator.<TestCase>comparingInt(tc -> tc.getFolder() == null ? -1
                        : treeOrder.getOrDefault(tc.getFolder().getId(), Integer.MAX_VALUE))
                .thenComparingLong(tc -> keyNumber(tc.getKey()))
                .thenComparing(TestCase::getKey);
    }

    static long keyNumber(String key) {
        String digits = key == null ? "" : key.substring(key.lastIndexOf('-') + 1);
        return digits.matches("\\d{1,18}") ? Long.parseLong(digits) : Long.MAX_VALUE;
    }
}
