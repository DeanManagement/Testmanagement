package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySessionNoteImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ExploratorySessionNoteImageRepository extends JpaRepository<ExploratorySessionNoteImage, UUID> {

    Optional<ExploratorySessionNoteImage> findByNoteId(UUID noteId);

    /** Which of these notes have an image, without loading any bytes. */
    @Query("SELECT i.note.id FROM ExploratorySessionNoteImage i WHERE i.note.id IN :noteIds")
    Set<UUID> findNoteIdsWithImage(@Param("noteIds") Collection<UUID> noteIds);
}
