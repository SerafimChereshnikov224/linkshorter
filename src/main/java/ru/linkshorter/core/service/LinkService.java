package ru.linkshorter.core.service;

import static ru.linkshorter.core.exception.LinkShorterException.Type.ACCESS_DENIED;
import static ru.linkshorter.core.exception.LinkShorterException.Type.NOT_FOUND;
import static ru.linkshorter.core.exception.LinkShorterException.Type.STORAGE;
import static ru.linkshorter.core.exception.LinkShorterException.Type.UNAVAILABLE;
import static ru.linkshorter.core.exception.LinkShorterException.Type.VALIDATION;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.repository.LinkRepository;

@RequiredArgsConstructor
public class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 32;

    private final LinkRepository linkRepository;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AppConfig config;
    private final Clock clock;

    public Link create(String rawUrl, Long maxVisits) {
        var url = urlValidator.validate(rawUrl);
        var limit = maxVisits == null ? config.defaultMaxVisits() : maxVisits;
        validateLimitRange(limit);
        var owner = sessionService.currentUser();
        var now = clock.instant();
        var expiresAt = now.plus(config.linkTtl());

        for (var attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            var code = codeGenerator.generate();
            if (!linkRepository.existsByCode(code)) {
                return linkRepository.save(Link.builder()
                        .code(code)
                        .originalUrl(url)
                        .ownerUuid(owner.toString())
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .maxVisits(limit)
                        .build());
            }
        }
        throw new LinkShorterException(
                STORAGE, "Не удалось подобрать уникальный короткий код, попробуйте ещё раз.");
    }

    public Link open(String rawCode) {
        var code = normalizeCode(rawCode);
        var link = linkRepository.findByCode(code).orElseThrow(() -> notFound(code));
        var now = clock.instant();

        if (link.isExpired(now)) {
            notifyExpiredOnce(link);
            linkRepository.deleteByCode(code);
            throw new LinkShorterException(
                    UNAVAILABLE,
                    "Ссылка " + display(code) + " недоступна: истёк срок действия. Ссылка удалена.");
        }
        if (link.isLimitReached()) {
            notifyLimitOnce(link);
            throw new LinkShorterException(
                    UNAVAILABLE,
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
        var owner = sessionService.currentUser().toString();
        return linkRepository.findByOwner(owner).stream()
                .sorted(Comparator.comparing(Link::getCreatedAt).reversed())
                .toList();
    }

    public Link findOwned(String rawCode) {
        var code = normalizeCode(rawCode);
        var link = linkRepository.findByCode(code).orElseThrow(() -> notFound(code));
        if (!link.getOwnerUuid().equals(sessionService.currentUser().toString())) {
            throw new LinkShorterException(
                    ACCESS_DENIED, "Ссылка " + display(code) + " принадлежит другому пользователю.");
        }
        return link;
    }

    public Link updateLimit(String rawCode, Long newLimit) {
        var link = findOwned(rawCode);
        if (link.isExpired(clock.instant())) {
            throw new LinkShorterException(
                    UNAVAILABLE, "Нельзя изменить лимит: срок действия ссылки истёк.");
        }

        if (newLimit == null) {
            link.setMaxVisits(null);
            link.setLimitNotified(false);
        } else {
            validateLimitRange(newLimit);
            if (newLimit <= link.getVisits()) {
                throw new LinkShorterException(
                        VALIDATION,
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
        linkRepository.deleteByCode(findOwned(rawCode).getCode());
    }

    public int purgeExpired() {
        var now = clock.instant();
        var removed = 0;
        for (var link : linkRepository.findAll()) {
            if (link.isExpired(now)) {
                notifyExpiredOnce(link);
                linkRepository.deleteByCode(link.getCode());
                removed++;
            }
        }
        return removed;
    }

    public List<Notification> pullNotifications() {
        return notificationService.readAllAndMarkRead(sessionService.currentUser().toString());
    }

    public long unreadNotificationCount() {
        return notificationService.unreadCount(sessionService.currentUser().toString());
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

    private void validateLimitRange(long maxVisits) {
        if (maxVisits <= 0) {
            throw new LinkShorterException(VALIDATION, "Лимит переходов должен быть положительным числом.");
        }
        if (maxVisits > config.maxAllowedVisits()) {
            throw new LinkShorterException(
                    VALIDATION, "Лимит переходов не может превышать " + config.maxAllowedVisits() + ".");
        }
    }

    private LinkShorterException notFound(String code) {
        return new LinkShorterException(NOT_FOUND, "Ссылка с кодом '" + code + "' не найдена.");
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new LinkShorterException(VALIDATION, "Не указан короткий код ссылки.");
        }
        var candidate = rawCode.trim();
        var lastSlash = candidate.lastIndexOf('/');
        if (lastSlash >= 0) {
            candidate = candidate.substring(lastSlash + 1);
        }
        if (candidate.isBlank()) {
            throw new LinkShorterException(VALIDATION, "Не указан короткий код ссылки.");
        }
        return candidate;
    }
}
