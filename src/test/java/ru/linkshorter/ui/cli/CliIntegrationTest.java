package ru.linkshorter.ui.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.linkshorter.config.AppConfig;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.port.LinkRepository;
import ru.linkshorter.core.port.NotificationRepository;
import ru.linkshorter.core.port.SessionRepository;
import ru.linkshorter.core.service.LinkService;
import ru.linkshorter.core.service.NotificationService;
import ru.linkshorter.core.service.ShortCodeGenerator;
import ru.linkshorter.core.service.UrlValidator;
import ru.linkshorter.infra.storage.JsonLinkRepository;
import ru.linkshorter.infra.storage.JsonNotificationRepository;
import ru.linkshorter.infra.storage.JsonSessionRepository;
import ru.linkshorter.infra.storage.JsonSupport;
import ru.linkshorter.support.MutableClock;
import ru.linkshorter.support.RecordingBrowserLauncher;

class CliIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String URL = "https://ru.stackoverflow.com";

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final RecordingBrowserLauncher browser = new RecordingBrowserLauncher();
    private MutableClock clock;
    private LinkService service;
    private Cli cli;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        ObjectMapper mapper = JsonSupport.mapper();
        AppConfig config = new AppConfig(
                Duration.ofMinutes(60), 100, 1_000_000, tempDir, "sho.rt/");

        LinkRepository links = new JsonLinkRepository(tempDir.resolve("links.json"), mapper);
        NotificationRepository notifications =
                new JsonNotificationRepository(tempDir.resolve("notifications.json"), mapper);
        SessionRepository sessions = new JsonSessionRepository(tempDir.resolve("session.json"), mapper);

        service = new LinkService(
                links,
                sessions,
                new NotificationService(notifications, config.shortLinkPrefix(), clock),
                new ShortCodeGenerator(7),
                new UrlValidator(),
                config,
                clock);
        cli = new Cli(
                service,
                browser,
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void createCommandPrintsShortLink() {
        int exitCode = cli.execute(new String[] {"create", URL, "--limit", "5"});

        assertThat(exitCode).isZero();
        assertThat(output()).contains("Ссылка создана: sho.rt/");
        assertThat(output()).contains("Лимит:      5");
    }

    @Test
    void openCommandLaunchesBrowserAndCountsVisit() {
        Link link = service.create(URL, 5L);

        int exitCode = cli.execute(new String[] {"open", link.getCode()});

        assertThat(exitCode).isZero();
        assertThat(browser.openedUrls()).containsExactly(URL);
        assertThat(output()).contains("Переход 1/5");
    }

    @Test
    void openCommandBlocksAfterLimit() {
        Link link = service.create(URL, 1L);
        cli.execute(new String[] {"open", link.getCode()});

        int exitCode = cli.execute(new String[] {"open", link.getCode()});

        assertThat(exitCode).isEqualTo(1);
        assertThat(output()).contains("исчерпан лимит переходов");
        assertThat(browser.openedUrls()).hasSize(1);
    }

    @Test
    void expiredLinksArePurgedBeforeEveryCommand() {
        service.create(URL, 5L);
        clock.advance(Duration.ofMinutes(61));

        int exitCode = cli.execute(new String[] {"list"});

        assertThat(exitCode).isZero();
        assertThat(output()).contains("У вас пока нет ссылок");
        assertThat(output()).contains("Непрочитанных уведомлений: 1");
    }

    @Test
    void deleteCommandRemovesLink() {
        Link link = service.create(URL, 5L);

        assertThat(cli.execute(new String[] {"delete", link.getCode()})).isZero();

        assertThat(output()).contains("Ссылка удалена");
        assertThat(cli.execute(new String[] {"list"})).isZero();
        assertThat(output()).contains("У вас пока нет ссылок");
    }

    @Test
    void notificationsCommandShowsLimitNotification() {
        Link link = service.create(URL, 1L);
        cli.execute(new String[] {"open", link.getCode()});

        assertThat(cli.execute(new String[] {"notifications"})).isZero();

        assertThat(output()).contains("Лимит переходов исчерпан");
    }

    @Test
    void helpCommandListsAllCommands() {
        cli.execute(new String[] {"help"});

        assertThat(output())
                .contains("create <url>")
                .contains("open <code>")
                .contains("notifications")
                .contains("delete <code>");
    }

    @Test
    void unknownCommandShowsHint() {
        int exitCode = cli.execute(new String[] {"frobnicate"});

        assertThat(exitCode).isZero();
        assertThat(output()).contains("Неизвестная команда").contains("help");
    }

    @Test
    void validationErrorIsReportedWithoutStackTrace() {
        int exitCode = cli.execute(new String[] {"create", "ftp://example.com"});

        assertThat(exitCode).isEqualTo(1);
        assertThat(output()).contains("Ошибка:").contains("http");
    }
}
