package ru.linkshorter.core.service;

import static ru.linkshorter.core.model.NotificationType.EXPIRED;
import static ru.linkshorter.core.model.NotificationType.LIMIT_REACHED;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.model.NotificationType;
import ru.linkshorter.core.repository.NotificationRepository;

@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final NotificationRepository repository;
    private final String shortLinkPrefix;
    private final Clock clock;

    public Notification notifyLimitReached(Link link) {
        var message = "Лимит переходов исчерпан: " + display(link.getCode())
                + " больше недоступна (совершено " + link.getVisits()
                + " из " + link.getMaxVisits() + " переходов).";
        return notify(link, LIMIT_REACHED, message);
    }

    public Notification notifyExpired(Link link) {
        var message = "Срок действия ссылки " + display(link.getCode())
                + " истёк (" + TIME_FORMAT.format(link.getExpiresAt())
                + "), ссылка удалена.";
        return notify(link, EXPIRED, message);
    }

    public List<Notification> readAllAndMarkRead(String ownerUuid) {
        var notifications = repository.findByOwner(ownerUuid);
        repository.markAllReadByOwner(ownerUuid, clock.instant());
        return notifications;
    }

    public long unreadCount(String ownerUuid) {
        return repository.countUnreadByOwner(ownerUuid);
    }

    private Notification notify(Link link, NotificationType type, String message) {
        return repository.save(Notification.builder()
                .id(UUID.randomUUID().toString())
                .ownerUuid(link.getOwnerUuid())
                .linkCode(link.getCode())
                .type(type)
                .message(message)
                .createdAt(clock.instant())
                .build());
    }

    private String display(String code) {
        return shortLinkPrefix + code;
    }
}
