package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One timestamped plain-text entry in a session's log. Its optional screenshot lives in
 * {@link ExploratorySessionNoteImage} and is deliberately not mapped here, so loading a log
 * never loads image bytes.
 */
@Entity
@Table(name = "exploratory_session_notes")
@Getter
@Setter
@NoArgsConstructor
public class ExploratorySessionNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExploratorySession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private SessionNoteType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
