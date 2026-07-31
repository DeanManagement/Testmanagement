package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.parameter.ParameterSetResponse;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseParameterSetRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parameter sets on a test case (PRD-015).
 *
 * <p>Capped per case: expansion multiplies results in every run the case appears in, so an
 * accidental thousand-row paste would quietly turn one run into a thousand executions.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParameterSetService {

    /** §4: a reasonable ceiling, high enough for boundary tables and low enough to notice. */
    private static final int MAX_SETS_PER_CASE = 50;
    private static final int MAX_KEYS_PER_SET = 50;

    private final TestCaseParameterSetRepository parameterSetRepository;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;

    public List<ParameterSetResponse> list(UUID projectId, UUID testCaseId) {
        requireCase(projectId, testCaseId);
        return parameterSetRepository.findByTestCaseIdOrderByOrderIndexAsc(testCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ParameterSetResponse create(UUID projectId, UUID testCaseId, SaveParameterSetRequest request) {
        requireCase(projectId, testCaseId);
        validate(request);

        if (parameterSetRepository.countByTestCaseId(testCaseId) >= MAX_SETS_PER_CASE) {
            throw new IllegalArgumentException(
                    "A test case may have at most " + MAX_SETS_PER_CASE + " parameter sets");
        }
        String name = request.name().trim();
        if (parameterSetRepository.existsByTestCaseIdAndName(testCaseId, name)) {
            throw new DuplicateKeyException("name", name);
        }

        TestCaseParameterSet set = new TestCaseParameterSet();
        set.setTestCaseId(testCaseId);
        set.setName(name);
        set.setValuesJson(serialise(request.values()));
        set.setOrderIndex(request.orderIndex() != null ? request.orderIndex()
                : (int) parameterSetRepository.countByTestCaseId(testCaseId));
        return toResponse(parameterSetRepository.save(set));
    }

    @Transactional
    public ParameterSetResponse update(UUID projectId, UUID testCaseId, UUID id,
                                       SaveParameterSetRequest request) {
        requireCase(projectId, testCaseId);
        validate(request);

        TestCaseParameterSet set = parameterSetRepository.findByIdAndTestCaseId(id, testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseParameterSet", id));

        String name = request.name().trim();
        if (!set.getName().equals(name) && parameterSetRepository.existsByTestCaseIdAndName(testCaseId, name)) {
            throw new DuplicateKeyException("name", name);
        }
        set.setName(name);
        set.setValuesJson(serialise(request.values()));
        if (request.orderIndex() != null) {
            set.setOrderIndex(request.orderIndex());
        }
        return toResponse(parameterSetRepository.save(set));
    }

    @Transactional
    public void delete(UUID projectId, UUID testCaseId, UUID id) {
        requireCase(projectId, testCaseId);
        TestCaseParameterSet set = parameterSetRepository.findByIdAndTestCaseId(id, testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseParameterSet", id));
        // Results already recorded keep their own copy of the values, so removing a set does not
        // rewrite history — it only changes what future runs expand into.
        parameterSetRepository.delete(set);
    }

    /** Sets for a case, used by run creation to decide whether to expand. */
    public List<TestCaseParameterSet> setsFor(UUID testCaseId) {
        return parameterSetRepository.findByTestCaseIdOrderByOrderIndexAsc(testCaseId);
    }

    public Map<String, String> valuesOf(TestCaseParameterSet set) {
        return deserialise(set.getValuesJson());
    }

    // ---- helpers ----------------------------------------------------------

    private void validate(SaveParameterSetRequest request) {
        if (request.values().size() > MAX_KEYS_PER_SET) {
            throw new IllegalArgumentException(
                    "A parameter set may have at most " + MAX_KEYS_PER_SET + " values");
        }
        for (String key : request.values().keySet()) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Parameter names cannot be empty");
            }
            // Keys have to match what the placeholder pattern can find, or the value is unreachable
            // and the author gets a silently unsubstituted step.
            if (!key.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException(
                        "Parameter name '" + key + "' may contain only letters, digits, dot, dash and underscore");
            }
        }
    }

    private TestCase requireCase(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    private ParameterSetResponse toResponse(TestCaseParameterSet set) {
        return new ParameterSetResponse(set.getId(), set.getName(),
                deserialise(set.getValuesJson()), set.getOrderIndex());
    }

    private String serialise(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (Exception e) {
            throw new IllegalArgumentException("Parameter values could not be stored");
        }
    }

    private Map<String, String> deserialise(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            // A corrupt row must not take out the whole editor.
            return Map.of();
        }
    }
}
