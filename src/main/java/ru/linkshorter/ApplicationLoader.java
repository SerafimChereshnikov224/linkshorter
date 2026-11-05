package ru.linkshorter;

import java.time.Clock;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.config.JsonConfig;
import ru.linkshorter.core.service.LinkService;
import ru.linkshorter.core.service.NotificationService;
import ru.linkshorter.core.service.SessionService;
import ru.linkshorter.core.service.ShortCodeGenerator;
import ru.linkshorter.core.service.UrlValidator;
import ru.linkshorter.infra.browser.DesktopBrowserLauncher;
import ru.linkshorter.infra.storage.JsonLinkRepository;
import ru.linkshorter.infra.storage.JsonNotificationRepository;
import ru.linkshorter.infra.storage.JsonSessionRepository;
import ru.linkshorter.ui.cli.Cli;

public final class ApplicationLoader {

    public static Cli loadCli() {
        var config = AppConfig.load();
        var mapper = JsonConfig.mapper();
        var clock = Clock.systemDefaultZone();
        var storage = config.storageDir();

        var linkRepository = new JsonLinkRepository(storage.resolve("links.json"), mapper);
        var notificationRepository =
                new JsonNotificationRepository(storage.resolve("notifications.json"), mapper);
        var sessionRepository = new JsonSessionRepository(storage.resolve("session.json"), mapper);

        var sessionService = new SessionService(sessionRepository);
        var notificationService =
                new NotificationService(notificationRepository, config.shortLinkPrefix(), clock);
        var linkService = new LinkService(
                linkRepository,
                sessionService,
                notificationService,
                new ShortCodeGenerator(config.shortCodeLength()),
                new UrlValidator(),
                config,
                clock);

        return new Cli(
                linkService,
                sessionService,
                new DesktopBrowserLauncher(),
                System.in,
                System.out);
    }
}
