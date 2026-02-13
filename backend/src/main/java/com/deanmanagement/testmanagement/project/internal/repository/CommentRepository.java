package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.Comment;
import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(CommentEntityType entityType, UUID entityId);
}
