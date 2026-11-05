package ru.linkshorter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.port.BrowserLauncher;
import ru.linkshorter.core.port.LinkRepository;
import ru.linkshorter.core.port.NotificationRepository;
import ru.linkshorter.core.port.SessionRepository;
import ru.linkshorter.core.service.LinkService;
import ru.linkshorter.core.service.NotificationService;
import ru.linkshorter.core.service.ShortCodeGenerator;
import ru.linkshorter.core.service.UrlValidator;
import ru.linkshorter.infra.browser.DesktopBrowserLauncher;
import ru.linkshorter.infra.storage.JsonLinkRepository;
import ru.linkshorter.infra.storage.JsonNotificationRepository;
import ru.linkshorter.infra.storage.JsonSessionRepository;
import ru.linkshorter.infra.storage.JsonSupport;
import ru.linkshorter.ui.cli.Cli;

public final class App {

    private static final int SHORT_CODE_LENGTH = 7;

    private App() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        ObjectMapper mapper = JsonSupport.mapper();
        Clock clock = Clock.systemDefaultZone();
        Path storage = config.storageDir();

        LinkRepository linkRepository = new JsonLinkRepository(storage.resolve("links.json"), mapper);
        NotificationRepository notificationRepository =
                new JsonNotificationRepository(storage.resolve("notifications.json"), mapper);
        SessionRepository sessionRepository =
                new JsonSessionRepository(storage.resolve("session.json"), mapper);

        NotificationService notificationService =
                new NotificationService(notificationRepository, config.shortLinkPrefix(), clock);
        LinkService linkService = new LinkService(
                linkRepository,
                sessionRepository,
                notificationService,
                new ShortCodeGenerator(SHORT_CODE_LENGTH),
                new UrlValidator(),
                config,
                clock);

        BrowserLauncher browserLauncher = new DesktopBrowserLauncher();
        Cli cli = new Cli(linkService, browserLauncher, System.in, System.out);

        if (args.length > 0) {
            System.exit(cli.execute(args));
        }
        cli.run();
    }
}
