package ru.linkshorter.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.exception.AccessDeniedException;
import ru.linkshorter.core.exception.LinkNotFoundException;
import ru.linkshorter.core.exception.LinkUnavailableException;
import ru.linkshorter.core.exception.ValidationException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.LinkStatus;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.model.NotificationType;
import ru.linkshorter.support.InMemoryLinkRepository;
import ru.linkshorter.support.InMemoryNotificationRepository;
import ru.linkshorter.support.InMemorySessionRepository;
import ru.linkshorter.support.MutableClock;

class LinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String URL = "https://www.baeldung.com/java-9-http-client";

    private MutableClock clock;
    private InMemoryLinkRepository linkRepository;
    private InMemoryNotificationRepository notificationRepository;
    private InMemorySessionRepository sessionRepository;
    private LinkService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        linkRepository = new InMemoryLinkRepository();
        notificationRepository = new InMemoryNotificationRepository();
        sessionRepository = new InMemorySessionRepository();
        AppConfig config = new AppConfig(
                Duration.ofHours(24), 100, 1_000_000, Path.of("data"), "sho.rt/");
        service = new LinkService(
                linkRepository,
                sessionRepository,
                new NotificationService(notificationRepository, "sho.rt/", clock),
                new ShortCodeGenerator(7),
                new UrlValidator(),
                config,
                clock);
    }

    @Test
    void generatesUserUuidOnFirstLinkCreation() {
        assertThat(sessionRepository.currentUser()).isEmpty();

        Link link = service.create(URL, null);

        assertThat(sessionRepository.currentUser()).isPresent();
        assertThat(link.getOwnerUuid()).isEqualTo(sessionRepository.currentUser().orElseThrow().toString());
    }

    @Test
    void appliesTtlAndDefaultLimitFromConfig() {
        Link link = service.create(URL, null);

        assertThat(link.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(link.getMaxVisits()).isEqualTo(100L);
        assertThat(link.getVisits()).isZero();
    }

    @Test
    void differentUsersGetDifferentCodesForSameUrl() {
        service.create(URL, null);
        service.login(UUID.randomUUID());
        Link second = service.create(URL, null);

        Link first = linkRepository.findAll().get(0);
        assertThat(second.getCode()).isNotEqualTo(first.getCode());
        assertThat(second.getOriginalUrl()).isEqualTo(URL);
    }

    @Test
    void userCanHaveSeveralLinksAndSeeOnlyOwn() {
        service.create("https://example.com/one", null);
        service.create("https://example.com/two", null);
        UUID firstOwner = service.currentUser();

        service.login(UUID.randomUUID());
        UUID secondOwner = service.currentUser();
        service.create("https://example.com/three", null);

        List<Link> mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getOwnerUuid()).isEqualTo(secondOwner.toString());

        service.login(firstOwner);
        assertThat(service.listMine()).hasSize(2);
    }

    @Test
    void rejectsInvalidLimitOnCreation() {
        assertThatThrownBy(() -> service.create(URL, 0L)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(URL, 1_000_001L))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create("not-a-url", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void openIncrementsVisitCounter() {
        Link created = service.create(URL, 3L);

        Link opened = service.open(created.getCode());

        assertThat(opened.getVisits()).isEqualTo(1);
        assertThat(linkRepository.findByCode(created.getCode()).orElseThrow().getVisits())
                .isEqualTo(1);
    }

    @Test
    void openIsBlockedWhenLimitReached() {
        Link created = service.create(URL, 2L);
        service.open(created.getCode());
        service.open(created.getCode());

        assertThatThrownBy(() -> service.open(created.getCode()))
                .isInstanceOf(LinkUnavailableException.class)
                .satisfies(exception -> assertThat(((LinkUnavailableException) exception).getStatus())
                        .isEqualTo(LinkStatus.LIMIT_REACHED));

        List<Notification> notifications =
                notificationRepository.findByOwner(created.getOwnerUuid());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.LIMIT_REACHED);
    }

    @Test
    void openIsBlockedAfterExpirationAndLinkIsRemoved() {
        Link created = service.create(URL, 10L);
        clock.advance(Duration.ofHours(24));

        assertThatThrownBy(() -> service.open(created.getCode()))
                .isInstanceOf(LinkUnavailableException.class)
                .satisfies(exception -> assertThat(((LinkUnavailableException) exception).getStatus())
                        .isEqualTo(LinkStatus.EXPIRED));

        assertThat(linkRepository.findByCode(created.getCode())).isEmpty();
        List<Notification> notifications =
                notificationRepository.findByOwner(created.getOwnerUuid());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.EXPIRED);
    }

    @Test
    void purgeExpiredRemovesOnlyExpiredLinks() {
        Link expired = service.create("https://example.com/old", null);
        clock.advance(Duration.ofHours(12));
        Link active = service.create("https://example.com/new", null);
        clock.advance(Duration.ofHours(13));

        int removed = service.purgeExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(linkRepository.findByCode(expired.getCode())).isEmpty();
        assertThat(linkRepository.findByCode(active.getCode())).isPresent();
        assertThat(notificationRepository.findByOwner(expired.getOwnerUuid())).hasSize(1);
    }

    @Test
    void onlyOwnerCanChangeLimit() {
        Link created = service.create(URL, 5L);
        service.login(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateLimit(created.getCode(), 10L))
                .isInstanceOf(AccessDeniedException.class);

        service.login(UUID.fromString(created.getOwnerUuid()));
        Link updated = service.updateLimit(created.getCode(), 10L);

        assertThat(updated.getMaxVisits()).isEqualTo(10L);
    }

    @Test
    void limitCanBeRemovedAndCannotBeLowerThanVisits() {
        Link created = service.create(URL, 5L);
        service.open(created.getCode());

        assertThatThrownBy(() -> service.updateLimit(created.getCode(), 1L))
                .isInstanceOf(ValidationException.class);

        Link updated = service.updateLimit(created.getCode(), null);

        assertThat(updated.getMaxVisits()).isNull();
        assertThat(service.open(created.getCode()).getVisits()).isEqualTo(2);
    }

    @Test
    void onlyOwnerCanDeleteLink() {
        Link created = service.create(URL, 1L);
        service.login(UUID.randomUUID());

        assertThatThrownBy(() -> service.delete(created.getCode()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(linkRepository.findByCode(created.getCode())).isPresent();

        service.login(UUID.fromString(created.getOwnerUuid()));
        service.delete(created.getCode());

        assertThat(linkRepository.findByCode(created.getCode())).isEmpty();
    }

    @Test
    void notificationsAreMarkedAsReadAfterPull() {
        Link created = service.create(URL, 1L);
        service.open(created.getCode());

        assertThat(service.unreadNotificationCount()).isEqualTo(1);
        assertThat(service.pullNotifications()).hasSize(1);
        assertThat(service.unreadNotificationCount()).isZero();
    }

    @Test
    void unknownCodeProducesNotFound() {
        service.create(URL, 1L);

        assertThatThrownBy(() -> service.open("zzzzzzz"))
                .isInstanceOf(LinkNotFoundException.class);
    }

    @Test
    void codeCanBePassedAsFullShortLink() {
        Link created = service.create(URL, 5L);

        assertThat(service.open("sho.rt/" + created.getCode()).getVisits()).isEqualTo(1);
    }
}
