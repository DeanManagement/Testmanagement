package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.notification.NotificationPreferenceDto;
import com.deanmanagement.testmanagement.project.internal.dto.notification.NotificationResponse;
import com.deanmanagement.testmanagement.project.internal.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
@Tag(name = "My Notifications", description = "Current user's in-app notifications and preferences")
@RequiredArgsConstructor
public class MyNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public Page<NotificationResponse> list(Authentication authentication,
                                           @RequestParam(defaultValue = "false") boolean unread,
                                           @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(authentication.getName());
        return notificationService.list(userId, unread, pageable);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable UUID id) {
        UUID userId = UUID.fromString(authentication.getName());
        notificationService.markRead(userId, id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        notificationService.markAllRead(userId);
    }

    @GetMapping("/notification-preferences")
    public List<NotificationPreferenceDto> getPreferences(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return notificationService.getPreferences(userId);
    }

    @PutMapping("/notification-preferences")
    public List<NotificationPreferenceDto> updatePreferences(Authentication authentication,
                                                             @Valid @RequestBody List<NotificationPreferenceDto> prefs) {
        UUID userId = UUID.fromString(authentication.getName());
        return notificationService.updatePreferences(userId, prefs);
    }
}
