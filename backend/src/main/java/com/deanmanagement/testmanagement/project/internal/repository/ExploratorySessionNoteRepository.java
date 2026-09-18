package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySessionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExploratorySessionNoteRepository extends JpaRepository<ExploratorySessionNote, UUID> {

    List<ExploratorySessionNote> findBySessionIdOrderByOccurredAtDescCreatedAtDesc(UUID sessionId);

    Optional<ExploratorySessionNote> findByIdAndSessionId(UUID id, UUID sessionId);
}
