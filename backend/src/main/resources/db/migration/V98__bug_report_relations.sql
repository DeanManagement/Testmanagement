-- TM46: bug reports linked by hand to test cases, requirements and other bug reports.
CREATE TABLE bug_report_test_cases (
    bug_report_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    test_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    PRIMARY KEY (bug_report_id, test_case_id)
);
CREATE INDEX idx_bug_report_test_cases_case ON bug_report_test_cases(test_case_id);

CREATE TABLE bug_report_requirements (
    bug_report_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    PRIMARY KEY (bug_report_id, requirement_id)
);
CREATE INDEX idx_bug_report_requirements_requirement ON bug_report_requirements(requirement_id);

-- Directed: "bug_report_id BLOCKS related_bug_id"; RELATES_TO is stored once and read both ways.
CREATE TABLE bug_report_relations (
    id UUID PRIMARY KEY,
    bug_report_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    related_bug_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    relation_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_bug_report_relations UNIQUE (bug_report_id, related_bug_id, relation_type),
    CONSTRAINT ck_bug_report_relations_not_self CHECK (bug_report_id <> related_bug_id)
);
CREATE INDEX idx_bug_report_relations_related ON bug_report_relations(related_bug_id);
