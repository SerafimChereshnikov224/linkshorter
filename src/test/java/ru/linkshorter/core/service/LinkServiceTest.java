package ru.linkshorter.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.linkshorter.core.exception.LinkShorterException.Type.ACCESS_DENIED;
import static ru.linkshorter.core.exception.LinkShorterException.Type.NOT_FOUND;
import static ru.linkshorter.core.exception.LinkShorterException.Type.UNAVAILABLE;
import static ru.linkshorter.core.exception.LinkShorterException.Type.VALIDATION;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.exception.LinkShorterException.Type;
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
    private SessionService sessionService;
    private LinkService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        linkRepository = new InMemoryLinkRepository();
        notificationRepository = new InMemoryNotificationRepository();
        sessionRepository = new InMemorySessionRepository();
        sessionService = new SessionService(sessionRepository);
        var config = new AppConfig(
                Duration.ofHours(24), 100, 1_000_000, 7, Path.of("data"), "sho.rt/");
        service = new LinkService(
                linkRepository,
                sessionService,
                new NotificationService(notificationRepository, "sho.rt/", clock),
                new ShortCodeGenerator(7),
                new UrlValidator(),
                config,
                clock);
    }

    private void assertError(ThrowingCallable callable, Type type) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        LinkShorterException.class,
                        exception -> assertThat(exception.type()).isEqualTo(type));
    }

    @Test
    void generatesUserUuidOnFirstLinkCreation() {
        assertThat(sessionRepository.currentUser()).isEmpty();

        var link = service.create(URL, null);

        assertThat(sessionRepository.currentUser()).isPresent();
        assertThat(link.getOwnerUuid()).isEqualTo(sessionService.currentUser().toString());
    }

    @Test
    void appliesTtlAndDefaultLimitFromConfig() {
        var link = service.create(URL, null);

        assertThat(link.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(link.getMaxVisits()).isEqualTo(100L);
        assertThat(link.getVisits()).isZero();
    }

    @Test
    void differentUsersGetDifferentCodesForSameUrl() {
        var first = service.create(URL, null);
        sessionService.login(UUID.randomUUID());
        var second = service.create(URL, null);

        assertThat(second.getCode()).isNotEqualTo(first.getCode());
        assertThat(second.getOriginalUrl()).isEqualTo(URL);
    }

    @Test
    void userCanHaveSeveralLinksAndSeeOnlyOwn() {
        service.create("https://example.com/one", null);
        service.create("https://example.com/two", null);
        var firstOwner = sessionService.currentUser();

        sessionService.login(UUID.randomUUID());
        var secondOwner = sessionService.currentUser();
        service.create("https://example.com/three", null);

        var mine = service.listMine();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getOwnerUuid()).isEqualTo(secondOwner.toString());

        sessionService.login(firstOwner);
        assertThat(service.listMine()).hasSize(2);
    }

    @Test
    void rejectsInvalidLimitOnCreation() {
        assertError(() -> service.create(URL, 0L), VALIDATION);
        assertError(() -> service.create(URL, 1_000_001L), VALIDATION);
        assertError(() -> service.create("not-a-url", null), VALIDATION);
    }

    @Test
    void openIncrementsVisitCounter() {
        var created = service.create(URL, 3L);

        var opened = service.open(created.getCode());

        assertThat(opened.getVisits()).isEqualTo(1);
        assertThat(linkRepository.findByCode(created.getCode()).orElseThrow().getVisits())
                .isEqualTo(1);
    }

    @Test
    void openIsBlockedWhenLimitReached() {
        var created = service.create(URL, 2L);
        service.open(created.getCode());
        service.open(created.getCode());

        assertError(() -> service.open(created.getCode()), UNAVAILABLE);

        var notifications = notificationRepository.findByOwner(created.getOwnerUuid());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.LIMIT_REACHED);
    }

    @Test
    void openIsBlockedAfterExpirationAndLinkIsRemoved() {
        var created = service.create(URL, 10L);
        clock.advance(Duration.ofHours(24));

        assertError(() -> service.open(created.getCode()), UNAVAILABLE);

        assertThat(linkRepository.findByCode(created.getCode())).isEmpty();
        var notifications = notificationRepository.findByOwner(created.getOwnerUuid());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.EXPIRED);
    }

    @Test
    void purgeExpiredRemovesOnlyExpiredLinks() {
        var expired = service.create("https://example.com/old", null);
        clock.advance(Duration.ofHours(12));
        var active = service.create("https://example.com/new", null);
        clock.advance(Duration.ofHours(13));

        var removed = service.purgeExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(linkRepository.findByCode(expired.getCode())).isEmpty();
        assertThat(linkRepository.findByCode(active.getCode())).isPresent();
        assertThat(notificationRepository.findByOwner(expired.getOwnerUuid())).hasSize(1);
    }

    @Test
    void onlyOwnerCanChangeLimit() {
        var created = service.create(URL, 5L);
        sessionService.login(UUID.randomUUID());

        assertError(() -> service.updateLimit(created.getCode(), 10L), ACCESS_DENIED);

        sessionService.login(UUID.fromString(created.getOwnerUuid()));
        var updated = service.updateLimit(created.getCode(), 10L);

        assertThat(updated.getMaxVisits()).isEqualTo(10L);
    }

    @Test
    void limitCanBeRemovedAndCannotBeLowerThanVisits() {
        var created = service.create(URL, 5L);
        service.open(created.getCode());

        assertError(() -> service.updateLimit(created.getCode(), 1L), VALIDATION);

        var updated = service.updateLimit(created.getCode(), null);

        assertThat(updated.getMaxVisits()).isNull();
        assertThat(service.open(created.getCode()).getVisits()).isEqualTo(2);
    }

    @Test
    void onlyOwnerCanDeleteLink() {
        var created = service.create(URL, 1L);
        sessionService.login(UUID.randomUUID());

        assertError(() -> service.delete(created.getCode()), ACCESS_DENIED);
        assertThat(linkRepository.findByCode(created.getCode())).isPresent();

        sessionService.login(UUID.fromString(created.getOwnerUuid()));
        service.delete(created.getCode());

        assertThat(linkRepository.findByCode(created.getCode())).isEmpty();
    }

    @Test
    void notificationsAreMarkedAsReadAfterPull() {
        var created = service.create(URL, 1L);
        service.open(created.getCode());

        assertThat(service.unreadNotificationCount()).isEqualTo(1);
        assertThat(service.pullNotifications()).hasSize(1);
        assertThat(service.unreadNotificationCount()).isZero();
    }

    @Test
    void unknownCodeProducesNotFound() {
        service.create(URL, 1L);

        assertError(() -> service.open("zzzzzzz"), NOT_FOUND);
    }

    @Test
    void codeCanBePassedAsFullShortLink() {
        var created = service.create(URL, 5L);

        assertThat(service.open("sho.rt/" + created.getCode()).getVisits()).isEqualTo(1);
    }
}
