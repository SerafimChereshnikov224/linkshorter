package ru.linkshorter.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.exception.AccessDeniedException;
import ru.linkshorter.core.exception.LinkNotFoundException;
import ru.linkshorter.core.exception.LinkUnavailableException;
import ru.linkshorter.core.exception.StorageException;
import ru.linkshorter.core.exception.ValidationException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.LinkStatus;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.port.LinkRepository;
import ru.linkshorter.core.port.SessionRepository;

public class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 32;

    private final LinkRepository linkRepository;
    private final SessionRepository sessionRepository;
    private final NotificationService notificationService;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AppConfig config;
    private final Clock clock;

    public LinkService(
            LinkRepository linkRepository,
            SessionRepository sessionRepository,
            NotificationService notificationService,
            ShortCodeGenerator codeGenerator,
            UrlValidator urlValidator,
            AppConfig config,
            Clock clock) {
        this.linkRepository = linkRepository;
        this.sessionRepository = sessionRepository;
        this.notificationService = notificationService;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.config = config;
        this.clock = clock;
    }

    public UUID currentUser() {
        Optional<UUID> existing = sessionRepository.currentUser();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID generated = UUID.randomUUID();
        sessionRepository.saveCurrentUser(generated);
        return generated;
    }

    public UUID login(UUID uuid) {
        if (uuid == null) {
            throw new ValidationException("UUID пользователя не может быть пустым.");
        }
        sessionRepository.saveCurrentUser(uuid);
        return uuid;
    }

    public Optional<UUID> peekUser() {
        return sessionRepository.currentUser();
    }

    public Link create(String rawUrl, Long maxVisits) {
        String url = urlValidator.validate(rawUrl);
        long resolvedLimit = resolveCreateLimit(maxVisits);
        UUID owner = currentUser();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.linkTtl());

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (linkRepository.existsByCode(code)) {
                continue;
            }
            Link link = new Link(code, url, owner.toString(), now, expiresAt, resolvedLimit);
            return linkRepository.save(link);
        }
        throw new StorageException(
                "Не удалось подобрать уникальный короткий код, попробуйте ещё раз.",
                new IllegalStateException("Exceeded " + MAX_CODE_ATTEMPTS + " attempts"));
    }

    public Link open(String rawCode) {
        String code = normalizeCode(rawCode);
        Link link = linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
        Instant now = clock.instant();

        if (link.isExpired(now)) {
            notifyExpiredOnce(link);
            linkRepository.deleteByCode(code);
            throw new LinkUnavailableException(
                    code,
                    LinkStatus.EXPIRED,
                    "Ссылка " + display(code) + " недоступна: истёк срок действия. Ссылка удалена.");
        }
        if (link.isLimitReached()) {
            notifyLimitOnce(link);
            throw new LinkUnavailableException(
                    code,
                    LinkStatus.LIMIT_REACHED,
                    "Ссылка " + display(code) + " недоступна: исчерпан лимит переходов ("
                            + link.getVisits() + " из " + link.getMaxVisits() + ").");
        }

        link.setVisits(link.getVisits() + 1);
        linkRepository.save(link);

        if (link.isLimitReached()) {
            notifyLimitOnce(link);
        }
        return link;
    }

    public List<Link> listMine() {
        String owner = currentUser().toString();
        return linkRepository.findByOwner(owner).stream()
                .sorted(Comparator.comparing(Link::getCreatedAt).reversed())
                .toList();
    }

    public Link findOwned(String rawCode) {
        String code = normalizeCode(rawCode);
        Link link = linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
        if (!link.getOwnerUuid().equals(currentUser().toString())) {
            throw new AccessDeniedException(
                    "Ссылка " + display(code) + " принадлежит другому пользователю.");
        }
        return link;
    }

    public Link updateLimit(String rawCode, Long newLimit) {
        Link link = findOwned(rawCode);
        Instant now = clock.instant();
        if (link.isExpired(now)) {
            throw new LinkUnavailableException(
                    link.getCode(),
                    LinkStatus.EXPIRED,
                    "Нельзя изменить лимит: срок действия ссылки истёк.");
        }

        if (newLimit == null) {
            link.setMaxVisits(null);
            link.setLimitNotified(false);
        } else {
            validateLimitRange(newLimit);
            if (newLimit <= link.getVisits()) {
                throw new ValidationException(
                        "Новый лимит (" + newLimit + ") должен быть больше уже совершённых переходов ("
                                + link.getVisits() + ").");
            }
            link.setMaxVisits(newLimit);
            if (link.getVisits() < newLimit) {
                link.setLimitNotified(false);
            }
        }
        return linkRepository.save(link);
    }

    public void delete(String rawCode) {
        Link link = findOwned(rawCode);
        linkRepository.deleteByCode(link.getCode());
    }

    public int purgeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Link link : linkRepository.findAll()) {
            if (!link.isExpired(now)) {
                continue;
            }
            notifyExpiredOnce(link);
            linkRepository.deleteByCode(link.getCode());
            removed++;
        }
        return removed;
    }

    public List<Notification> pullNotifications() {
        return notificationService.readAllAndMarkRead(currentUser().toString());
    }

    public long unreadNotificationCount() {
        return notificationService.unreadCount(currentUser().toString());
    }

    public String display(String code) {
        return config.shortLinkPrefix() + code;
    }

    private void notifyLimitOnce(Link link) {
        if (link.isLimitNotified()) {
            return;
        }
        link.setLimitNotified(true);
        linkRepository.save(link);
        notificationService.notifyLimitReached(link);
    }

    private void notifyExpiredOnce(Link link) {
        if (link.isExpiredNotified()) {
            return;
        }
        link.setExpiredNotified(true);
        linkRepository.save(link);
        notificationService.notifyExpired(link);
    }

    private long resolveCreateLimit(Long maxVisits) {
        if (maxVisits == null) {
            return config.defaultMaxVisits();
        }
        validateLimitRange(maxVisits);
        return maxVisits;
    }

    private void validateLimitRange(long maxVisits) {
        if (maxVisits <= 0) {
            throw new ValidationException("Лимит переходов должен быть положительным числом.");
        }
        if (maxVisits > config.maxAllowedVisits()) {
            throw new ValidationException(
                    "Лимит переходов не может превышать " + config.maxAllowedVisits() + ".");
        }
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ValidationException("Не указан короткий код ссылки.");
        }
        String candidate = rawCode.trim();
        int lastSlash = candidate.lastIndexOf('/');
        if (lastSlash >= 0) {
            candidate = candidate.substring(lastSlash + 1);
        }
        if (candidate.isBlank()) {
            throw new ValidationException("Не указан короткий код ссылки.");
        }
        return candidate;
    }
}
