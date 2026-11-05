package ru.linkshorter.core.service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.model.NotificationType;
import ru.linkshorter.core.port.NotificationRepository;

public class NotificationService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final NotificationRepository repository;
    private final String shortLinkPrefix;
    private final Clock clock;

    public NotificationService(
            NotificationRepository repository, String shortLinkPrefix, Clock clock) {
        this.repository = repository;
        this.shortLinkPrefix = shortLinkPrefix;
        this.clock = clock;
    }

    public Notification notifyLimitReached(Link link) {
        String message = "Лимит переходов исчерпан: " + display(link.getCode())
                + " больше недоступна (совершено " + link.getVisits()
                + " из " + link.getMaxVisits() + " переходов).";
        return notify(link, NotificationType.LIMIT_REACHED, message);
    }

    public Notification notifyExpired(Link link) {
        String message = "Срок действия ссылки " + display(link.getCode())
                + " истёк (" + TIME_FORMAT.format(link.getExpiresAt())
                + "), ссылка удалена.";
        return notify(link, NotificationType.EXPIRED, message);
    }

    private Notification notify(Link link, NotificationType type, String message) {
        Notification notification = new Notification(
                UUID.randomUUID().toString(),
                link.getOwnerUuid(),
                link.getCode(),
                type,
                message,
                clock.instant());
        return repository.save(notification);
    }

    public List<Notification> readAllAndMarkRead(String ownerUuid) {
        List<Notification> notifications = repository.findByOwner(ownerUuid);
        repository.markAllReadByOwner(ownerUuid, clock.instant());
        return notifications;
    }

    public long unreadCount(String ownerUuid) {
        return repository.countUnreadByOwner(ownerUuid);
    }

    private String display(String code) {
        return shortLinkPrefix + code;
    }
}
