package ru.linkshorter.core.model;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Link {

    private String code;
    private String originalUrl;
    private String ownerUuid;
    private Instant createdAt;
    private Instant expiresAt;
    private Long maxVisits;
    private long visits;
    private boolean limitNotified;
    private boolean expiredNotified;

    public Link(
            String code,
            String originalUrl,
            String ownerUuid,
            Instant createdAt,
            Instant expiresAt,
            Long maxVisits) {
        this.code = Objects.requireNonNull(code, "code");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.maxVisits = maxVisits;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isLimitReached() {
        return maxVisits != null && visits >= maxVisits;
    }

    public boolean isAvailable(Instant now) {
        return !isExpired(now) && !isLimitReached();
    }

    public LinkStatus status(Instant now) {
        if (isExpired(now)) {
            return LinkStatus.EXPIRED;
        }
        if (isLimitReached()) {
            return LinkStatus.LIMIT_REACHED;
        }
        return LinkStatus.ACTIVE;
    }

    public Long remainingVisits() {
        if (maxVisits == null) {
            return null;
        }
        return Math.max(0, maxVisits - visits);
    }
}
