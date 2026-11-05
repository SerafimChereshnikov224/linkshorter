package ru.linkshorter.core.port;

import java.time.Instant;
import java.util.List;
import ru.linkshorter.core.model.Notification;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findByOwner(String ownerUuid);

    long countUnreadByOwner(String ownerUuid);

    void markAllReadByOwner(String ownerUuid, Instant readAt);
}
