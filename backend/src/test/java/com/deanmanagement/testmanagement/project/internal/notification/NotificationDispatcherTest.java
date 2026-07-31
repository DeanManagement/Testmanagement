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
import com.deanmanagement.testmanagement.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private EntityWatcherRepository watcherRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private NotificationEmailService emailService;
    @Mock
    private UserService userService;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID WATCHER = UUID.randomUUID();
    private static final UUID RUN = UUID.randomUUID();

    private void memberOfProject(UUID userId) {
        when(projectMemberRepository.findMemberUserIds(eq(PROJECT), anyCollection()))
                .thenReturn(Set.of(userId));
    }

    private void noPreferencesAndNoRecentNotifications() {
        when(preferenceRepository.findByUserIdInAndAction(anyCollection(), eq(AuditAction.UPDATED)))
                .thenReturn(List.of());
        when(notificationRepository.findRecentlyNotifiedUserIds(
                anyCollection(), eq(RUN), eq(AuditAction.UPDATED), any()))
                .thenReturn(Set.of());
    }

    private EntityWatcher watcher(UUID userId) {
        EntityWatcher w = new EntityWatcher();
        w.setUserId(userId);
        w.setEntityType(WatchableEntityType.TEST_RUN);
        w.setEntityId(RUN);
        return w;
    }

    @SuppressWarnings("unchecked")
    private List<Notification> savedNotifications() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void watcherIsNotified() {
        memberOfProject(WATCHER);
        noPreferencesAndNoRecentNotifications();
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(WATCHER)));

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        List<Notification> saved = savedNotifications();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getUserId()).isEqualTo(WATCHER);
        assertThat(saved.getFirst().getEntityType()).isEqualTo(WatchableEntityType.TEST_RUN);
        assertThat(saved.getFirst().getAction()).isEqualTo(AuditAction.UPDATED);
    }

    @Test
    void actorIsNotNotified() {
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(ACTOR)));

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void inAppOptOutSuppressesNotification() {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(WATCHER);
        pref.setAction(AuditAction.UPDATED);
        pref.setInApp(false);

        memberOfProject(WATCHER);
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(WATCHER)));
        when(preferenceRepository.findByUserIdInAndAction(anyCollection(), eq(AuditAction.UPDATED)))
                .thenReturn(List.of(pref));
        when(notificationRepository.findRecentlyNotifiedUserIds(
                anyCollection(), eq(RUN), eq(AuditAction.UPDATED), any()))
                .thenReturn(Set.of());

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void duplicateWithinWindowSuppressed() {
        memberOfProject(WATCHER);
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(WATCHER)));
        when(preferenceRepository.findByUserIdInAndAction(anyCollection(), eq(AuditAction.UPDATED)))
                .thenReturn(List.of());
        when(notificationRepository.findRecentlyNotifiedUserIds(
                anyCollection(), eq(RUN), eq(AuditAction.UPDATED), any()))
                .thenReturn(Set.of(WATCHER));

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void nonWatchableTypeIsIgnored() {
        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_CASE, RUN, "tc");

        verify(notificationRepository, never()).saveAll(any());
    }

    // PRD-021: a watcher whose membership was revoked must not keep receiving updates.
    @Test
    void revokedMemberIsNotNotified() {
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(WATCHER)));
        when(projectMemberRepository.findMemberUserIds(eq(PROJECT), anyCollection()))
                .thenReturn(Set.of());
        when(projectAccessService.isSystemAdmin(WATCHER)).thenReturn(false);

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void systemAdminWatcherStillNotifiedWithoutMembership() {
        when(watcherRepository.findByEntityTypeAndEntityId(WatchableEntityType.TEST_RUN, RUN))
                .thenReturn(List.of(watcher(WATCHER)));
        when(projectMemberRepository.findMemberUserIds(eq(PROJECT), anyCollection()))
                .thenReturn(Set.of());
        when(projectAccessService.isSystemAdmin(WATCHER)).thenReturn(true);
        noPreferencesAndNoRecentNotifications();

        dispatcher.dispatch(PROJECT, ACTOR, AuditAction.UPDATED, AuditEntityType.TEST_RUN, RUN, "Smoke run");

        assertThat(savedNotifications()).hasSize(1);
    }
}
