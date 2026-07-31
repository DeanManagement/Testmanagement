package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.search.SearchResponse;
import com.deanmanagement.testmanagement.project.internal.service.SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Cross-entity full-text search scoped to the caller's projects")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResponse search(Authentication authentication,
                                 @RequestParam String q,
                                 @RequestParam(required = false) List<String> types,
                                 @RequestParam(required = false) UUID projectId,
                                 @RequestParam(defaultValue = "20") int limit) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isSystemAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        Set<String> typeSet = types == null ? null : Set.copyOf(types);
        return searchService.search(userId, isSystemAdmin, q, typeSet, projectId, limit);
    }
}
