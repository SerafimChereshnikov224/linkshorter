package ru.linkshorter.core.model;

import static ru.linkshorter.core.model.LinkStatus.ACTIVE;
import static ru.linkshorter.core.model.LinkStatus.EXPIRED;
import static ru.linkshorter.core.model.LinkStatus.LIMIT_REACHED;

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
            return EXPIRED;
        }
        if (isLimitReached()) {
            return LIMIT_REACHED;
        }
        return ACTIVE;
    }

    public Long remainingVisits() {
        if (maxVisits == null) {
            return null;
        }
        return Math.max(0, maxVisits - visits);
    }
}
