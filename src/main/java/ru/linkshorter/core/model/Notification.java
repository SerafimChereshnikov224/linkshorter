package ru.linkshorter.core.model;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Notification {

    private String id;
    private String ownerUuid;
    private String linkCode;
    private NotificationType type;
    private String message;
    private Instant createdAt;
    private Instant readAt;

    public Notification(
            String id,
            String ownerUuid,
            String linkCode,
            NotificationType type,
            String message,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.linkCode = Objects.requireNonNull(linkCode, "linkCode");
        this.type = Objects.requireNonNull(type, "type");
        this.message = Objects.requireNonNull(message, "message");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean isRead() {
        return readAt != null;
    }
}
