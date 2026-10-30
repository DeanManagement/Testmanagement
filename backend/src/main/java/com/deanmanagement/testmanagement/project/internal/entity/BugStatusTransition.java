package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** TM47: a bug may move from one status to the other. A project with none allows any move. */
@Entity
@Table(name = "bug_status_transitions")
@Getter
@Setter
@NoArgsConstructor
public class BugStatusTransition extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "from_status_id", nullable = false)
    private UUID fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private UUID toStatusId;
}
