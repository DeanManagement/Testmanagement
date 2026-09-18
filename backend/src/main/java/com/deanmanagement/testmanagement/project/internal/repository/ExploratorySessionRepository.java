package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExploratorySessionRepository
        extends JpaRepository<ExploratorySession, UUID>, JpaSpecificationExecutor<ExploratorySession> {

    Optional<ExploratorySession> findByIdAndProjectId(UUID id, UUID projectId);

    List<ExploratorySession> findByTestPlanIdOrderByCreatedAtAsc(UUID testPlanId);

    @Query("SELECT s FROM ExploratorySession s JOIN FETCH s.project WHERE s.tester.id = :testerId "
            + "AND s.status IN :statuses ORDER BY s.createdAt DESC")
    List<ExploratorySession> findByTesterAndStatuses(@Param("testerId") UUID testerId,
                                                     @Param("statuses") Collection<TestRunStatus> statuses);
}
