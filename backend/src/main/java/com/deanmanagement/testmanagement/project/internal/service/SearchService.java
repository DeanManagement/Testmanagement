package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.search.SearchHit;
import com.deanmanagement.testmanagement.project.internal.dto.search.SearchResponse;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-entity search scoped to the caller's project membership (PRD-007). Uses Postgres full-text
 * ({@code tsvector}/{@code ts_rank}) when {@code app.search.full-text=true}, otherwise a portable
 * {@code LIKE} fallback (used by the H2 dev/test profile).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_LIMIT = 50;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.search.full-text:true}")
    private boolean fullText;

    public SearchResponse search(UUID userId, boolean isSystemAdmin, String q,
                                 Set<String> types, UUID projectId, int limit) {
        String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(List.of(), List.of(), List.of(), List.of());
        }
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);

        return new SearchResponse(
                wants(types, "testCase") ? searchTestCases(userId, isSystemAdmin, query, projectId, capped) : List.of(),
                wants(types, "testRun") ? searchTestRuns(userId, isSystemAdmin, query, projectId, capped) : List.of(),
                wants(types, "bugReport") ? searchBugReports(userId, isSystemAdmin, query, projectId, capped) : List.of(),
                wants(types, "project") ? searchProjects(userId, isSystemAdmin, query, projectId, capped) : List.of()
        );
    }

    private boolean wants(Set<String> types, String type) {
        return types == null || types.isEmpty() || types.contains(type);
    }

    // ---- Test cases ----

    @SuppressWarnings("unchecked")
    private List<SearchHit> searchTestCases(UUID userId, boolean admin, String q, UUID projectId, int limit) {
        List<TestCase> rows;
        if (fullText) {
            String sql = "SELECT * FROM test_cases tc WHERE search_vector @@ plainto_tsquery('simple', :q) "
                    + memberSql("tc.project_id", admin, projectId)
                    + " ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :q)) DESC, tc.updated_at DESC LIMIT :limit";
            Query query = em.createNativeQuery(sql, TestCase.class);
            bindFullText(query, q, userId, admin, projectId, limit);
            rows = query.getResultList();
        } else {
            String jpql = "SELECT tc FROM TestCase tc WHERE "
                    + memberJpql("tc.project", admin, projectId)
                    + " AND (LOWER(tc.title) LIKE :q OR LOWER(tc.key) LIKE :q OR LOWER(tc.description) LIKE :q)"
                    + " ORDER BY tc.updatedAt DESC";
            rows = bindLike(em.createQuery(jpql, TestCase.class), q, userId, admin, projectId, limit).getResultList();
        }
        return rows.stream().map(tc -> new SearchHit("testCase", tc.getId(), tc.getKey(), tc.getTitle(),
                tc.getProject().getId(), snippet(tc.getDescription()))).toList();
    }

    // ---- Test runs ----

    @SuppressWarnings("unchecked")
    private List<SearchHit> searchTestRuns(UUID userId, boolean admin, String q, UUID projectId, int limit) {
        List<TestRun> rows;
        if (fullText) {
            String sql = "SELECT * FROM test_runs tr WHERE search_vector @@ plainto_tsquery('simple', :q) "
                    + memberSql("tr.project_id", admin, projectId)
                    + " ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :q)) DESC, tr.updated_at DESC LIMIT :limit";
            Query query = em.createNativeQuery(sql, TestRun.class);
            bindFullText(query, q, userId, admin, projectId, limit);
            rows = query.getResultList();
        } else {
            String jpql = "SELECT tr FROM TestRun tr WHERE "
                    + memberJpql("tr.project", admin, projectId)
                    + " AND (LOWER(tr.name) LIKE :q OR LOWER(tr.key) LIKE :q OR LOWER(tr.environment) LIKE :q)"
                    + " ORDER BY tr.updatedAt DESC";
            rows = bindLike(em.createQuery(jpql, TestRun.class), q, userId, admin, projectId, limit).getResultList();
        }
        return rows.stream().map(tr -> new SearchHit("testRun", tr.getId(), tr.getKey(), tr.getName(),
                tr.getProject().getId(), snippet(tr.getEnvironment()))).toList();
    }

    // ---- Bug reports ----

    @SuppressWarnings("unchecked")
    private List<SearchHit> searchBugReports(UUID userId, boolean admin, String q, UUID projectId, int limit) {
        List<BugReport> rows;
        if (fullText) {
            String sql = "SELECT * FROM bug_reports br WHERE search_vector @@ plainto_tsquery('simple', :q) "
                    + memberSql("br.project_id", admin, projectId)
                    + " ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :q)) DESC, br.updated_at DESC LIMIT :limit";
            Query query = em.createNativeQuery(sql, BugReport.class);
            bindFullText(query, q, userId, admin, projectId, limit);
            rows = query.getResultList();
        } else {
            String jpql = "SELECT br FROM BugReport br WHERE "
                    + memberJpql("br.project", admin, projectId)
                    + " AND (LOWER(br.title) LIKE :q OR LOWER(br.description) LIKE :q)"
                    + " ORDER BY br.updatedAt DESC";
            rows = bindLike(em.createQuery(jpql, BugReport.class), q, userId, admin, projectId, limit).getResultList();
        }
        return rows.stream().map(br -> new SearchHit("bugReport", br.getId(), null, br.getTitle(),
                br.getProject().getId(), snippet(br.getDescription()))).toList();
    }

    // ---- Projects ----

    @SuppressWarnings("unchecked")
    private List<SearchHit> searchProjects(UUID userId, boolean admin, String q, UUID projectId, int limit) {
        List<Project> rows;
        if (fullText) {
            String sql = "SELECT * FROM projects p WHERE search_vector @@ plainto_tsquery('simple', :q) "
                    + memberSql("p.id", admin, projectId)
                    + " ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :q)) DESC, p.updated_at DESC LIMIT :limit";
            Query query = em.createNativeQuery(sql, Project.class);
            bindFullText(query, q, userId, admin, projectId, limit);
            rows = query.getResultList();
        } else {
            String jpql = "SELECT p FROM Project p WHERE "
                    + (admin ? "1=1" : "EXISTS (SELECT 1 FROM ProjectMember m WHERE m.project = p AND m.user.id = :userId)")
                    + (projectId != null ? " AND p.id = :projectId" : "")
                    + " AND (LOWER(p.name) LIKE :q OR LOWER(p.key) LIKE :q OR LOWER(p.description) LIKE :q)"
                    + " ORDER BY p.updatedAt DESC";
            var query = em.createQuery(jpql, Project.class).setMaxResults(limit);
            query.setParameter("q", like(q));
            if (!admin) query.setParameter("userId", userId);
            if (projectId != null) query.setParameter("projectId", projectId);
            rows = query.getResultList();
        }
        return rows.stream().map(p -> new SearchHit("project", p.getId(), p.getKey(), p.getName(),
                p.getId(), snippet(p.getDescription()))).toList();
    }

    // ---- Shared helpers ----

    private String memberJpql(String projectPath, boolean admin, UUID projectId) {
        String base = admin
                ? "1=1"
                : "EXISTS (SELECT 1 FROM ProjectMember m WHERE m.project = " + projectPath + " AND m.user.id = :userId)";
        return projectId != null ? base + " AND " + projectPath + ".id = :projectId" : base;
    }

    private String memberSql(String projectIdColumn, boolean admin, UUID projectId) {
        StringBuilder sb = new StringBuilder();
        if (!admin) {
            sb.append(" AND EXISTS (SELECT 1 FROM project_members m WHERE m.project_id = ")
                    .append(projectIdColumn).append(" AND m.user_id = :userId)");
        }
        if (projectId != null) {
            sb.append(" AND ").append(projectIdColumn).append(" = :projectId");
        }
        return sb.toString();
    }

    private <T> jakarta.persistence.TypedQuery<T> bindLike(jakarta.persistence.TypedQuery<T> query, String q,
                                                          UUID userId, boolean admin, UUID projectId, int limit) {
        query.setMaxResults(limit);
        query.setParameter("q", like(q));
        if (!admin) query.setParameter("userId", userId);
        if (projectId != null) query.setParameter("projectId", projectId);
        return query;
    }

    private void bindFullText(Query query, String q, UUID userId, boolean admin, UUID projectId, int limit) {
        query.setParameter("q", q);
        query.setParameter("limit", limit);
        if (!admin) query.setParameter("userId", userId);
        if (projectId != null) query.setParameter("projectId", projectId);
    }

    private String like(String q) {
        return "%" + q.toLowerCase() + "%";
    }

    private String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() > 140 ? trimmed.substring(0, 140) + "…" : trimmed;
    }
}
