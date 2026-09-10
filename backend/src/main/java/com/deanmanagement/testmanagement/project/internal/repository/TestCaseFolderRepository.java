package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface TestCaseFolderRepository extends JpaRepository<TestCaseFolder, UUID> {

    List<TestCaseFolder> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    List<TestCaseFolder> findByProjectIdAndParentIsNullOrderBySortOrderAsc(UUID projectId);

    long countByProjectId(UUID projectId);

    /**
     * The folder itself plus every descendant, walked in memory over the project's folders (a
     * project has few folders, and this keeps the SQL vendor-neutral). Always contains
     * {@code folderId}, so a folder from another project simply yields itself and matches nothing.
     */
    default Set<UUID> findSubtreeIds(UUID projectId, UUID folderId) {
        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (TestCaseFolder folder : findByProjectIdOrderBySortOrderAsc(projectId)) {
            if (folder.getParent() != null) {
                childrenByParent.computeIfAbsent(folder.getParent().getId(), k -> new ArrayList<>())
                        .add(folder.getId());
            }
        }
        Set<UUID> ids = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(folderId);
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (ids.add(current)) {
                childrenByParent.getOrDefault(current, List.of()).forEach(pending::push);
            }
        }
        return ids;
    }
}
