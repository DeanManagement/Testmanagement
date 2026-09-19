package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseParameterSetRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.ParameterSetService;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.ExportCase;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.FeatureDoc;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.RuleBlock;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test cases as {@code .feature} files (PRD-040 §3.4), in the shape the import creates: a folder is
 * a Feature, its sub-folders are Rules. Exporting one folder gives that folder's feature; exporting
 * the project gives one feature per top-level folder, plus "Unfiled" for cases in no folder.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GherkinExporter {

    static final String UNFILED = "Unfiled";
    private static final String FEATURE_EXTENSION = ".feature";
    private static final String ZIP_NAME = "features.zip";
    private static final String FLATTENED_NOTE =
            "Folders nested below Rule level are exported into their Rule; the structure does not round-trip.";

    private final TestCaseRepository testCaseRepository;
    private final TestCaseFolderRepository folderRepository;
    private final TestCaseParameterSetRepository parameterSetRepository;
    private final ParameterSetService parameterSetService;

    /** One file downloads as itself; several as a ZIP. {@code zip} tells the caller which. */
    public record ExportFile(String fileName, byte[] content, boolean zip) {
    }

    public ExportFile export(UUID projectId, UUID folderId) {
        List<TestCaseFolder> folders = folderRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<UUID, List<TestCaseFolder>> children = new HashMap<>();
        List<TestCaseFolder> roots = new ArrayList<>();
        for (TestCaseFolder folder : folders) {
            if (folder.getParent() == null) {
                roots.add(folder);
            } else {
                children.computeIfAbsent(folder.getParent().getId(), k -> new ArrayList<>()).add(folder);
            }
        }
        Map<UUID, List<ExportCase>> casesByFolder = casesByFolder(projectId);

        Map<String, String> files = new LinkedHashMap<>();
        if (folderId != null) {
            TestCaseFolder folder = folders.stream().filter(f -> f.getId().equals(folderId)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", folderId));
            addFeature(files, folder, children, casesByFolder);
        } else {
            roots.forEach(root -> addFeature(files, root, children, casesByFolder));
            List<ExportCase> unfiled = casesByFolder.getOrDefault(null, List.of());
            if (!unfiled.isEmpty()) {
                put(files, UNFILED, GherkinWriter.write(new FeatureDoc(UNFILED, unfiled, List.of(), List.of())));
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("There are no test cases to export");
        }
        if (files.size() == 1) {
            Map.Entry<String, String> only = files.entrySet().iterator().next();
            return new ExportFile(only.getKey(), only.getValue().getBytes(StandardCharsets.UTF_8), false);
        }
        return new ExportFile(ZIP_NAME, zip(files), true);
    }

    /** A folder with no cases anywhere below it has nothing to say and gets no file. */
    private void addFeature(Map<String, String> files, TestCaseFolder folder,
                            Map<UUID, List<TestCaseFolder>> children, Map<UUID, List<ExportCase>> casesByFolder) {
        List<RuleBlock> rules = new ArrayList<>();
        boolean flattened = false;
        for (TestCaseFolder ruleFolder : children.getOrDefault(folder.getId(), List.of())) {
            List<ExportCase> ruleCases = new ArrayList<>(casesByFolder.getOrDefault(ruleFolder.getId(), List.of()));
            for (TestCaseFolder deeper : descendants(ruleFolder, children)) {
                List<ExportCase> deeperCases = casesByFolder.getOrDefault(deeper.getId(), List.of());
                flattened |= !deeperCases.isEmpty();
                ruleCases.addAll(deeperCases);
            }
            if (!ruleCases.isEmpty()) {
                rules.add(new RuleBlock(ruleFolder.getName(), ruleCases));
            }
        }
        List<ExportCase> direct = casesByFolder.getOrDefault(folder.getId(), List.of());
        if (direct.isEmpty() && rules.isEmpty()) {
            return;
        }
        List<String> notes = flattened ? List.of(FLATTENED_NOTE) : List.of();
        put(files, folder.getName(), GherkinWriter.write(new FeatureDoc(folder.getName(), direct, rules, notes)));
    }

    private static List<TestCaseFolder> descendants(TestCaseFolder folder, Map<UUID, List<TestCaseFolder>> children) {
        List<TestCaseFolder> all = new ArrayList<>();
        for (TestCaseFolder child : children.getOrDefault(folder.getId(), List.of())) {
            all.add(child);
            all.addAll(descendants(child, children));
        }
        return all;
    }

    /** Cases per folder id (null = no folder), in key order, which is the order they were created in. */
    private Map<UUID, List<ExportCase>> casesByFolder(UUID projectId) {
        List<TestCase> cases = testCaseRepository.findByProjectIdWithSteps(projectId).stream()
                .sorted(Comparator.comparingLong(GherkinExporter::keyNumber))
                .toList();
        Map<UUID, List<SaveParameterSetRequest>> sets = parameterSetRepository
                .findByTestCaseIdInOrderByOrderIndexAsc(cases.stream().map(TestCase::getId).toList()).stream()
                .collect(Collectors.groupingBy(TestCaseParameterSet::getTestCaseId, LinkedHashMap::new,
                        Collectors.mapping(this::toSet, Collectors.toList())));
        Map<UUID, List<ExportCase>> byFolder = new HashMap<>();
        for (TestCase tc : cases) {
            UUID folderId = tc.getFolder() == null ? null : tc.getFolder().getId();
            byFolder.computeIfAbsent(folderId, k -> new ArrayList<>())
                    .add(toExportCase(tc, sets.getOrDefault(tc.getId(), List.of())));
        }
        return byFolder;
    }

    /** Keys are always {@code <PROJECT>-<n>}. */
    private static long keyNumber(TestCase tc) {
        return Long.parseLong(tc.getKey().substring(tc.getKey().lastIndexOf('-') + 1));
    }

    private SaveParameterSetRequest toSet(TestCaseParameterSet set) {
        return new SaveParameterSetRequest(set.getName(), parameterSetService.valuesOf(set), set.getOrderIndex());
    }

    private static ExportCase toExportCase(TestCase tc, List<SaveParameterSetRequest> sets) {
        List<TestStepRequest> steps = tc.getSteps().stream()
                .map(s -> new TestStepRequest(s.getAction(), s.getExpectedResult(), s.getTestData()))
                .toList();
        return new ExportCase(tc.getKey(), tc.getTitle(), tc.getDescription(), tc.getPreconditions(),
                tc.getPriority(), Set.copyOf(tc.getLabels()), steps, sets);
    }

    /** File names from folder names; two folders of the same name get "-2", "-3". */
    private static void put(Map<String, String> files, String name, String content) {
        String base = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").strip();
        if (base.isEmpty()) {
            base = UNFILED;
        }
        String fileName = base + FEATURE_EXTENSION;
        Set<String> taken = new HashSet<>(files.keySet());
        for (int n = 2; taken.contains(fileName); n++) {
            fileName = base + "-" + n + FEATURE_EXTENSION;
        }
        files.put(fileName, content);
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
