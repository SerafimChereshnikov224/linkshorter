package ru.linkshorter.core.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private String id;
    private String ownerUuid;
    private String linkCode;
    private NotificationType type;
    private String message;
    private Instant createdAt;
    private Instant readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
