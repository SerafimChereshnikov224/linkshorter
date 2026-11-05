package ru.linkshorter.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.repository.NotificationRepository;

public class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<String, Notification> storage = new LinkedHashMap<>();

    @Override
    public Notification save(Notification notification) {
        storage.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public List<Notification> findByOwner(String ownerUuid) {
        return storage.values().stream()
                .filter(notification -> notification.getOwnerUuid().equals(ownerUuid))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }

    @Override
    public long countUnreadByOwner(String ownerUuid) {
        return storage.values().stream()
                .filter(notification -> notification.getOwnerUuid().equals(ownerUuid))
                .filter(notification -> !notification.isRead())
                .count();
    }

    @Override
    public void markAllReadByOwner(String ownerUuid, Instant readAt) {
        for (var notification : storage.values()) {
            if (notification.getOwnerUuid().equals(ownerUuid) && !notification.isRead()) {
                notification.setReadAt(readAt);
            }
        }
    }

    public List<Notification> all() {
        return new ArrayList<>(storage.values());
    }
}
