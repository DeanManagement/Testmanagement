package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectId(UUID projectId);

    List<ProjectMember> findByUserId(UUID userId);

    Optional<ProjectMember> findByUserIdAndProjectId(UUID userId, UUID projectId);

    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);

    @Query("SELECT pm.user.id FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.user.id IN :userIds")
    Set<UUID> findMemberUserIds(@Param("projectId") UUID projectId, @Param("userIds") Collection<UUID> userIds);
}
