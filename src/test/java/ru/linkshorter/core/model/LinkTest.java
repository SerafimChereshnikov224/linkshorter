package ru.linkshorter.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    private Link link(long visits, Long maxVisits) {
        return Link.builder()
                .code("abc1234")
                .originalUrl("https://example.com")
                .ownerUuid("user-1")
                .createdAt(CREATED_AT)
                .expiresAt(CREATED_AT.plusSeconds(3600))
                .maxVisits(maxVisits)
                .visits(visits)
                .build();
    }

    @Test
    void linkIsActiveWhileNotExpiredAndLimitNotReached() {
        Link link = link(1, 5L);

        assertThat(link.status(CREATED_AT)).isEqualTo(LinkStatus.ACTIVE);
        assertThat(link.isAvailable(CREATED_AT)).isTrue();
        assertThat(link.remainingVisits()).isEqualTo(4L);
    }

    @Test
    void linkExpiresAtExpirationBoundary() {
        Link link = link(0, 5L);

        assertThat(link.isExpired(CREATED_AT.plusSeconds(3599))).isFalse();
        assertThat(link.isExpired(CREATED_AT.plusSeconds(3600))).isTrue();
        assertThat(link.status(CREATED_AT.plusSeconds(3600))).isEqualTo(LinkStatus.EXPIRED);
    }

    @Test
    void linkIsBlockedWhenLimitReached() {
        Link link = link(5, 5L);

        assertThat(link.isLimitReached()).isTrue();
        assertThat(link.status(CREATED_AT)).isEqualTo(LinkStatus.LIMIT_REACHED);
        assertThat(link.isAvailable(CREATED_AT)).isFalse();
        assertThat(link.remainingVisits()).isEqualTo(0L);
    }

    @Test
    void linkWithoutLimitNeverReachesLimit() {
        Link link = link(1000, null);

        assertThat(link.isLimitReached()).isFalse();
        assertThat(link.remainingVisits()).isNull();
        assertThat(link.status(CREATED_AT)).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void expiredStatusHasPriorityOverLimit() {
        Link link = link(5, 5L);

        assertThat(link.status(CREATED_AT.plusSeconds(3601))).isEqualTo(LinkStatus.EXPIRED);
    }
}
