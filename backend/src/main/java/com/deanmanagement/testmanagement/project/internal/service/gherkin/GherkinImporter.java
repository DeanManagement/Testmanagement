package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse.ImportError;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldWriteMode;
import com.deanmanagement.testmanagement.project.internal.service.ParameterSetService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseImportExportService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinFileParser.Scenario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Writes parsed scenarios as test cases (PRD-040 §3.3). Untagged scenarios are created; a scenario
 * tagged {@code @tm:<KEY>} updates that case, and is left alone when nothing changed so a re-import
 * of an unchanged file adds no versions.
 *
 * <p>Each scenario is checked completely before anything is written for it, so a refused one is
 * skipped without leaving half a case behind or marking the caller's transaction rollback-only.
 */
@Service
@RequiredArgsConstructor
public class GherkinImporter {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_FOLDER_NAME_LENGTH = 255;
    private static final int MAX_SET_NAME_LENGTH = 200;
    private static final int MAX_SETS_PER_CASE = 50;
    private static final int MAX_IMPORT_SCENARIOS = TestCaseImportExportService.MAX_IMPORT_ROWS;
    private static final String FEATURE_EXTENSION = ".feature";
    private static final String ZIP_EXTENSION = ".zip";

    private final TestCaseRepository testCaseRepository;
    private final TestCaseFolderRepository folderRepository;
    private final TestCaseService testCaseService;
    private final TestCaseFolderService folderService;
    private final ParameterSetService parameterSetService;

    /** What the caller already settled, the folder's project scope included: where the files go and how. */
    public record Target(UUID projectId, UUID folderId, boolean reviewRequired, boolean dryRun, UUID userId) {
    }

    public static boolean isGherkinUpload(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(FEATURE_EXTENSION) || name.endsWith(ZIP_EXTENSION);
    }

    /** A {@code .feature} file, or a ZIP whose {@code .feature} entries are imported in name order. */
    @Transactional
    public ImportResultResponse importUpload(Target target, String fileName, byte[] content) {
        Map<String, String> files = fileName.toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION)
                ? FeatureZip.read(content)
                : Map.of(fileName, new String(content, StandardCharsets.UTF_8));
        List<Scenario> scenarios = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        files.forEach((name, text) -> {
            GherkinFileParser.ParsedFile parsed = GherkinFileParser.parse(name, stripBom(text));
            scenarios.addAll(parsed.scenarios());
            warnings.addAll(parsed.warnings());
        });
        if (scenarios.size() > MAX_IMPORT_SCENARIOS) {
            throw new IllegalArgumentException("Import exceeds the limit of " + MAX_IMPORT_SCENARIOS
                    + " test cases (" + scenarios.size() + " scenarios); split the upload");
        }
        return importScenarios(target, scenarios, warnings);
    }

    private ImportResultResponse importScenarios(Target target, List<Scenario> scenarios, List<String> fileWarnings) {
        Counts counts = new Counts();
        List<ImportError> errors = new ArrayList<>();
        List<ImportError> warnings = new ArrayList<>();
        fileWarnings.forEach(w -> warnings.add(new ImportError(0, w)));

        FolderResolver folders = new FolderResolver(target);
        Set<String> keysSeen = new HashSet<>();
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            int row = i + 1;
            scenario.warnings().forEach(w -> warnings.add(new ImportError(row, scenario.location() + ": " + w)));
            try {
                importOne(target, scenario, folders, keysSeen, counts, warnings, row);
            } catch (IllegalArgumentException e) {
                counts.skipped++;
                errors.add(new ImportError(row, scenario.location() + ": " + e.getMessage()));
            }
        }
        warnings.addAll(folders.unmatchedCases(keysSeen));
        return new ImportResultResponse(counts.created, counts.skipped, target.dryRun(), errors, warnings,
                counts.updated, counts.unchanged);
    }

    private static final class Counts {
        int created;
        int updated;
        int unchanged;
        int skipped;
    }

    private void importOne(Target target, Scenario scenario, FolderResolver folders, Set<String> keysSeen,
                           Counts counts, List<ImportError> warnings, int row) {
        validate(scenario);
        if (scenario.key() == null) {
            UUID folderId = folders.resolve(scenario.folderPath());
            // Its new key joins the claimed ones, so the case is not reported as unmatched below.
            create(target, scenario, folderId, warnings, row).ifPresent(keysSeen::add);
            counts.created++;
            return;
        }
        if (!keysSeen.add(scenario.key())) {
            throw new IllegalArgumentException("@tm:" + scenario.key() + " appears on more than one scenario");
        }
        TestCase existing = testCaseRepository.findByKeyAndProjectId(scenario.key(), target.projectId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "@tm:" + scenario.key() + " is not a test case of this project"));
        folders.markTouched(scenario.folderPath());
        List<TestCaseParameterSet> currentSets = parameterSetService.setsFor(existing.getId());
        boolean caseChanged = isCaseChanged(existing, scenario);
        boolean setsChanged = isSetsChanged(currentSets, scenario.parameterSets());
        if (!caseChanged && !setsChanged) {
            counts.unchanged++;
            return;
        }
        counts.updated++;
        if (target.dryRun()) {
            return;
        }
        if (caseChanged) {
            testCaseService.update(target.projectId(), existing.getId(), toUpdate(scenario), target.userId(),
                    CustomFieldWriteMode.MACHINE);
        }
        if (setsChanged) {
            // Results keep their own copy of the values, so replacing the sets rewrites no history.
            currentSets.forEach(s -> parameterSetService.delete(target.projectId(), existing.getId(), s.getId()));
            addSets(target.projectId(), existing.getId(), scenario.parameterSets());
        }
    }

    /** The new case's key, or empty in a dry run. */
    private Optional<String> create(Target target, Scenario scenario, UUID folderId, List<ImportError> warnings, int row) {
        TestCaseStatus status = TestCaseStatus.ACTIVE;
        if (target.reviewRequired()) {
            // Importing is not approving (PRD-033), same as the CSV import.
            status = TestCaseStatus.IN_REVIEW;
            warnings.add(new ImportError(row, scenario.location() + ": imported as IN_REVIEW: this project requires review"));
        }
        if (target.dryRun()) {
            return Optional.empty();
        }
        Priority priority = scenario.priority() == null ? Priority.MEDIUM : scenario.priority();
        CreateTestCaseRequest request = new CreateTestCaseRequest(scenario.title(), scenario.description(),
                scenario.preconditions(), priority, status, scenario.labels(), scenario.steps(), folderId);
        TestCaseResponse created = testCaseService.create(target.projectId(), request, target.userId(),
                CustomFieldWriteMode.MACHINE);
        addSets(target.projectId(), created.id(), scenario.parameterSets());
        return Optional.of(created.key());
    }

    private void addSets(UUID projectId, UUID caseId, List<SaveParameterSetRequest> sets) {
        sets.forEach(set -> parameterSetService.create(projectId, caseId, set));
    }

    /** Everything a write could refuse, checked before the first write. */
    private static void validate(Scenario scenario) {
        if (!scenario.problems().isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", scenario.problems()));
        }
        if (scenario.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("the scenario name is longer than " + MAX_TITLE_LENGTH + " characters");
        }
        if (scenario.folderPath().stream().anyMatch(name -> name.length() > MAX_FOLDER_NAME_LENGTH)) {
            throw new IllegalArgumentException("a Feature or Rule name is longer than " + MAX_FOLDER_NAME_LENGTH + " characters");
        }
        if (scenario.parameterSets().size() > MAX_SETS_PER_CASE) {
            throw new IllegalArgumentException("more than " + MAX_SETS_PER_CASE + " example rows");
        }
        if (scenario.parameterSets().stream().anyMatch(s -> s.name().length() > MAX_SET_NAME_LENGTH)) {
            throw new IllegalArgumentException("an Examples name is longer than " + MAX_SET_NAME_LENGTH + " characters");
        }
    }

    /**
     * Priority and status are only compared when the file says something about them: a scenario
     * without {@code @priority:} leaves the case's priority alone, and status is never the file's.
     */
    private static UpdateTestCaseRequest toUpdate(Scenario scenario) {
        // "" rather than null clears: the file is the source of truth, and it has no description.
        return new UpdateTestCaseRequest(scenario.title(), nullToEmpty(scenario.description()),
                nullToEmpty(scenario.preconditions()), scenario.priority(), null, scenario.labels(),
                scenario.steps());
    }

    private static boolean isCaseChanged(TestCase existing, Scenario scenario) {
        List<TestStepRequest> currentSteps = existing.getSteps().stream()
                .map(s -> new TestStepRequest(s.getAction(), nullToEmpty(s.getExpectedResult()), nullToEmpty(s.getTestData())))
                .toList();
        List<TestStepRequest> newSteps = scenario.steps().stream()
                .map(s -> new TestStepRequest(s.action(), nullToEmpty(s.expectedResult()), nullToEmpty(s.testData())))
                .toList();
        return !existing.getTitle().equals(scenario.title())
                || !nullToEmpty(existing.getDescription()).equals(nullToEmpty(scenario.description()))
                || !nullToEmpty(existing.getPreconditions()).equals(nullToEmpty(scenario.preconditions()))
                || (scenario.priority() != null && scenario.priority() != existing.getPriority())
                || !new HashSet<>(existing.getLabels()).equals(scenario.labels())
                || !currentSteps.equals(newSteps);
    }

    private boolean isSetsChanged(List<TestCaseParameterSet> current, List<SaveParameterSetRequest> wanted) {
        if (current.size() != wanted.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).getName().equals(wanted.get(i).name())
                    || !parameterSetService.valuesOf(current.get(i)).equals(wanted.get(i).values())) {
                return true;
            }
        }
        return false;
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Feature/Rule names → folders under the target, reusing a same-named folder under the same
     * parent. In a dry run folders that would be created get a placeholder id and are not written.
     */
    private final class FolderResolver {

        private final Target target;
        /** (parent id or null) → child name → id. */
        private final Map<UUID, Map<String, UUID>> children = new HashMap<>();
        /** Folders that existed before this import and that the upload maps onto. */
        private final Set<UUID> existingTouched = new LinkedHashSet<>();
        private final Set<UUID> placeholders = new HashSet<>();

        FolderResolver(Target target) {
            this.target = target;
            for (TestCaseFolder folder : folderRepository.findByProjectIdOrderBySortOrderAsc(target.projectId())) {
                UUID parent = folder.getParent() == null ? null : folder.getParent().getId();
                children.computeIfAbsent(parent, k -> new HashMap<>()).putIfAbsent(folder.getName(), folder.getId());
            }
        }

        UUID resolve(List<String> path) {
            UUID parent = target.folderId();
            for (String name : path) {
                UUID found = children.computeIfAbsent(parent, k -> new HashMap<>()).get(name);
                if (found == null) {
                    found = create(name, parent);
                    children.get(parent).put(name, found);
                } else if (!placeholders.contains(found)) {
                    existingTouched.add(found);
                }
                parent = found;
            }
            return parent;
        }

        /** Walks the existing part of the path only: a keyed case is not moved, so nothing is created. */
        void markTouched(List<String> path) {
            UUID parent = target.folderId();
            for (String name : path) {
                UUID found = children.getOrDefault(parent, Map.of()).get(name);
                if (found == null) {
                    return;
                }
                if (!placeholders.contains(found)) {
                    existingTouched.add(found);
                }
                parent = found;
            }
        }

        private UUID create(String name, UUID parent) {
            if (target.dryRun()) {
                UUID placeholder = UUID.randomUUID();
                placeholders.add(placeholder);
                return placeholder;
            }
            UUID id = folderService.create(target.projectId(), new CreateTestCaseFolderRequest(name, parent),
                    target.userId()).id();
            placeholders.add(id); // new this import: nothing in it can be unmatched
            return id;
        }

        /**
         * Cases already in a folder the upload maps onto, that no scenario claimed by key. Reported,
         * never deleted: removal stays a human decision (PRD-040 §2).
         */
        List<ImportError> unmatchedCases(Set<String> keysSeen) {
            List<ImportError> unmatched = new ArrayList<>();
            for (UUID folderId : existingTouched) {
                for (TestCase tc : testCaseRepository.findByProjectIdAndFolderIdOrderByCreatedAtDesc(target.projectId(), folderId)) {
                    if (!keysSeen.contains(tc.getKey())) {
                        unmatched.add(new ImportError(0, tc.getKey() + " \"" + tc.getTitle() + "\" in folder \""
                                + tc.getFolder().getName() + "\" has no scenario in the upload"));
                    }
                }
            }
            return unmatched;
        }
    }
}
