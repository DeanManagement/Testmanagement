package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_results")
@Getter
@Setter
@NoArgsConstructor
public class TestResult extends BaseEntity {

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestResultStatus status;

    /**
     * When the result left PENDING (PRD-036); null while pending, and for results recorded before
     * this existed. Kept in step by {@link #setStatus}, never by callers.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "executed_at")
    private Instant executedAt;

    /**
     * Who executed it (PRD-048): set with {@code executedAt}, so it names the tester, not whoever
     * edited the result last. Null while pending, and for anonymous CI uploads.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "executed_by")
    private UUID executedBy;

    /** Measured effort in milliseconds (PRD-036): the execution timer, a manual edit, or a CI report. */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private String defectLink;

    /**
     * Which version of the test case this result executed (PRD-011). Null for results recorded
     * before versioning existed — the wording they ran against was never captured, and claiming
     * they ran v1 would be a false audit record.
     */
    @Column(name = "executed_version")
    private Integer executedVersion;

    /**
     * Which parameter set this result executed, when the case is parameterized (PRD-015). Null for
     * an ordinary case, which is the overwhelmingly common shape.
     */
    @Column(name = "parameter_set_name", length = 200)
    private String parameterSetName;

    /**
     * The values used, stored on the result rather than looked up. Editing or deleting the set
     * afterwards must not change what a past execution says it ran with.
     */
    @Column(name = "parameter_values_json", columnDefinition = "TEXT")
    private String parameterValuesJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private TestCase testCase;

    @OneToMany(mappedBy = "testResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<StepResult> stepResults = new ArrayList<>();

    /**
     * The one place {@code executedAt} and {@code executedBy} are decided, so every path that sets a status follows the
     * same rule (PRD-036 §3.2): leaving PENDING stamps it, returning to PENDING clears it, and a
     * correction such as PASSED to FAILED keeps it, because the result was already executed. The
     * executor is explicit, not read from a security context, so every caller has to say who it is.
     * Hibernate reads fields directly, so loading a row never passes through here.
     */
    public void setStatus(TestResultStatus status, UUID executor) {
        if (status == TestResultStatus.PENDING) {
            this.executedAt = null;
            this.executedBy = null;
        } else if (status != null && this.executedAt == null) {
            this.executedAt = Instant.now();
            this.executedBy = executor;
        }
        this.status = status;
    }
}
