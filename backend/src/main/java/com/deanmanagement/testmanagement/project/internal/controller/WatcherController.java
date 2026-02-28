package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.watcher.WatchRequest;
import com.deanmanagement.testmanagement.project.internal.dto.watcher.WatchedItemResponse;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.service.WatchedItemsService;
import com.deanmanagement.testmanagement.project.internal.service.WatcherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/watchers")
@Tag(name = "Watchers", description = "Entity watcher endpoints")
@RequiredArgsConstructor
public class WatcherController {

    private final WatcherService watcherService;
    private final WatchedItemsService watchedItemsService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void watch(@Valid @RequestBody WatchRequest request, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        watcherService.watch(userId, request.entityType(), request.entityId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatch(@RequestParam WatchableEntityType entityType,
                        @RequestParam UUID entityId,
                        Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        watcherService.unwatch(userId, entityType, entityId);
    }

    @GetMapping("/check")
    public Map<String, Boolean> isWatching(@RequestParam WatchableEntityType entityType,
                                           @RequestParam UUID entityId,
                                           Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean watching = watcherService.isWatching(userId, entityType, entityId);
        return Map.of("watching", watching);
    }

    @GetMapping("/my-items")
    public List<WatchedItemResponse> getMyWatchedItems(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return watchedItemsService.getWatchedItems(userId);
    }
}
