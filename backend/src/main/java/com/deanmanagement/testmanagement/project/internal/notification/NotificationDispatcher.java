package com.deanmanagement.testmanagement.project.internal.notification;

import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.Notification;
import com.deanmanagement.testmanagement.project.internal.entity.NotificationPreference;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.repository.EntityWatcherRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.NotificationPreferenceRepository;
import com.deanmanagement.testmanagement.project.internal.repository.NotificationRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fans out a persisted audit event to the watchers of the affected entity (PRD-006): creates an
 * in-app {@link Notification} per watcher (excluding the actor and honoring opt-outs) and optionally
 * sends an email. Hooked off {@code AuditService.log} so there is a single, reliable trigger.
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    /** Suppress duplicate notifications for the same (user, entity, action) within this window. */
    private static final long DEDUP_WINDOW_SECONDS = 5;

    private final EntityWatcherRepository watcherRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationEmailService emailService;
    private final UserService userService;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;

    public void dispatch(UUID projectId, UUID actorId, AuditAction action,
                         AuditEntityType entityType, UUID entityId, String entityName) {
        WatchableEntityType watchable = toWatchable(entityType);
        if (watchable == null || entityId == null) {
            return;
        }

        List<UUID> candidateIds = watcherRepository.findByEntityTypeAndEntityId(watchable, entityId).stream()
                .map(EntityWatcher::getUserId)
                .filter(userId -> !userId.equals(actorId)) // never notify the actor of their own action
                .distinct()
                .toList();
        if (candidateIds.isEmpty()) {
            return;
        }

        // PRD-021 defense in depth: a watcher whose project membership has been revoked
        // (or who watched before access checks existed) must not keep receiving updates.
        // Membership is one batch query; the system-admin fallback only runs for non-members.
        Set<UUID> memberIds = projectId == null
                ? Set.of()
                : projectMemberRepository.findMemberUserIds(projectId, candidateIds);
        List<UUID> recipientIds = candidateIds.stream()
                .filter(userId -> projectId == null
                        || memberIds.contains(userId)
                        || projectAccessService.isSystemAdmin(userId))
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        String actorName = actorId == null ? null : userService.findEntityById(actorId)
                .map(User::getDisplayName).orElse(null);
        Map<UUID, NotificationPreference> prefs = preferenceRepository
                .findByUserIdInAndAction(recipientIds, action).stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, pref -> pref));
        Instant dedupCutoff = Instant.now().minusSeconds(DEDUP_WINDOW_SECONDS);
        Set<UUID> recentlyNotified = notificationRepository
                .findRecentlyNotifiedUserIds(recipientIds, entityId, action, dedupCutoff);

        List<Notification> notifications = new ArrayList<>();
        boolean emailEnabled = emailService.isEnabled();
        for (UUID userId : recipientIds) {
            NotificationPreference pref = prefs.get(userId);
            boolean inApp = pref == null || pref.isInApp();
            boolean email = pref != null && pref.isEmail();

            if (inApp && !recentlyNotified.contains(userId)) {
                Notification notification = new Notification();
                notification.setUserId(userId);
                notification.setProjectId(projectId);
                notification.setEntityType(watchable);
                notification.setEntityId(entityId);
                notification.setAction(action);
                notification.setEntityName(entityName);
                notification.setActorName(actorName);
                notification.setCreatedAt(Instant.now());
                notifications.add(notification);
            }

            if (email && emailEnabled) {
                userService.findEntityById(userId).map(User::getEmail).ifPresent(to ->
                        emailService.send(to, "[Testmanagement] " + watchable + " " + action,
                                actorName + " " + action + " " + watchable + " " + (entityName != null ? entityName : "")));
            }
        }
        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    private WatchableEntityType toWatchable(AuditEntityType type) {
        return switch (type) {
            case TEST_PLAN -> WatchableEntityType.TEST_PLAN;
            case TEST_RUN -> WatchableEntityType.TEST_RUN;
            case BUG_REPORT -> WatchableEntityType.BUG_REPORT;
            default -> null;
        };
    }
}
