package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.notification.NotificationPreferenceDto;
import com.deanmanagement.testmanagement.project.internal.dto.notification.NotificationResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Notification;
import com.deanmanagement.testmanagement.project.internal.entity.NotificationPreference;
import com.deanmanagement.testmanagement.project.internal.repository.NotificationPreferenceRepository;
import com.deanmanagement.testmanagement.project.internal.repository.NotificationRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public Page<NotificationResponse> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(this::toResponse);
    }

    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    public List<NotificationPreferenceDto> getPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId).stream()
                .map(p -> new NotificationPreferenceDto(p.getAction(), p.isInApp(), p.isEmail()))
                .toList();
    }

    @Transactional
    public List<NotificationPreferenceDto> updatePreferences(UUID userId, List<NotificationPreferenceDto> prefs) {
        for (NotificationPreferenceDto dto : prefs) {
            NotificationPreference pref = preferenceRepository.findByUserIdAndAction(userId, dto.action())
                    .orElseGet(() -> {
                        NotificationPreference p = new NotificationPreference();
                        p.setUserId(userId);
                        p.setAction(dto.action());
                        return p;
                    });
            pref.setInApp(dto.inApp());
            pref.setEmail(dto.email());
            preferenceRepository.save(pref);
        }
        return getPreferences(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getProjectId(),
                n.getEntityType(),
                n.getEntityId(),
                n.getAction(),
                n.getEntityName(),
                n.getActorName(),
                n.getReadAt() != null,
                n.getCreatedAt()
        );
    }
}
