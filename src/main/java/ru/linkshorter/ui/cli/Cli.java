package ru.linkshorter.ui.cli;

import static ru.linkshorter.core.exception.LinkShorterException.Type.VALIDATION;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ru.linkshorter.core.browser.BrowserLauncher;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.service.LinkService;
import ru.linkshorter.core.service.SessionService;

@RequiredArgsConstructor
public class Cli {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final LinkService linkService;
    private final SessionService sessionService;
    private final BrowserLauncher browserLauncher;
    private final InputStream in;
    private final PrintStream out;
    private boolean running = true;

    public void run() {
        printWelcome();
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        while (running) {
            out.print("> ");
            out.flush();
            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                out.println("Ошибка чтения ввода: " + e.getMessage());
                return;
            }
            if (line == null) {
                break;
            }
            if (!line.isBlank()) {
                execute(line.trim().split("\\s+"));
            }
        }
        out.println("До свидания!");
    }

    public int execute(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            return 0;
        }
        try {
            linkService.purgeExpired();
            dispatch(args);
            return 0;
        } catch (LinkShorterException | IllegalArgumentException e) {
            out.println("Ошибка: " + e.getMessage());
            return 1;
        }
    }

    private void dispatch(String[] args) {
        var command = CliCommand.from(args[0]);
        if (command.isEmpty()) {
            out.println(
                    "Неизвестная команда '" + args[0] + "'. Введите 'help' для списка команд.");
            return;
        }
        switch (command.get()) {
            case CREATE -> create(args);
            case OPEN -> open(args);
            case LIST -> list();
            case SHOW -> show(args);
            case LIMIT -> limit(args);
            case DELETE -> delete(args);
            case NOTIFICATIONS -> notifications();
            case WHOAMI -> whoami();
            case USER -> login(args);
            case HELP -> printHelp();
            case EXIT -> running = false;
        }
    }

    private void create(String[] args) {
        requireArgs(args, 2, "create <url> [--limit N]");
        var url = args[1];
        Long maxVisits = null;
        for (var i = 2; i < args.length; i++) {
            var option = args[i];
            if (option.equals("--limit") || option.equals("-l")) {
                if (i + 1 >= args.length) {
                    throw new LinkShorterException(
                            VALIDATION, "После " + option + " нужно указать число.");
                }
                maxVisits = parseLimit(args[++i]);
            } else {
                throw new LinkShorterException(
                        VALIDATION,
                        "Неизвестный параметр '" + option + "'. Используйте: create <url> [--limit N]");
            }
        }

        var link = linkService.create(url, maxVisits);
        out.println("Ссылка создана: " + linkService.display(link.getCode()));
        out.println("  Оригинал:   " + link.getOriginalUrl());
        out.println("  Владелец:   " + link.getOwnerUuid());
        out.println("  Лимит:      " + describeLimit(link));
        out.println("  Действует:  до " + DATE_FORMAT.format(link.getExpiresAt()));
    }

    private void open(String[] args) {
        requireArgs(args, 2, "open <code>");
        var link = linkService.open(args[1]);
        out.println("Переход " + link.getVisits() + "/" + describeLimit(link)
                + ": открываю " + link.getOriginalUrl());
        browserLauncher.open(link.getOriginalUrl());
        if (link.isLimitReached()) {
            out.println("Внимание: это был последний доступный переход по ссылке.");
        }
    }

    private void list() {
        var links = linkService.listMine();
        var unread = linkService.unreadNotificationCount();
        if (links.isEmpty()) {
            out.println("У вас пока нет ссылок. Создайте первую: create <url> [--limit N]");
        } else {
            out.printf("%-10s %-14s %-10s %-20s %s%n", "КОД", "СТАТУС", "ПЕРЕХОДЫ", "ИСТЕКАЕТ", "АДРЕС");
            for (var link : links) {
                out.printf(
                        "%-10s %-14s %-10s %-20s %s%n",
                        link.getCode(),
                        link.status(Instant.now()),
                        link.getVisits() + "/" + describeLimit(link),
                        DATE_FORMAT.format(link.getExpiresAt()),
                        shorten(link.getOriginalUrl(), 60));
            }
        }
        if (unread > 0) {
            out.println("Непрочитанных уведомлений: " + unread + ". Введите 'notifications'.");
        }
    }

    private void show(String[] args) {
        requireArgs(args, 2, "show <code>");
        var link = linkService.findOwned(args[1]);
        out.println("Код:         " + linkService.display(link.getCode()));
        out.println("Оригинал:    " + link.getOriginalUrl());
        out.println("Владелец:    " + link.getOwnerUuid());
        out.println("Статус:      " + link.status(Instant.now()));
        out.println("Переходы:    " + link.getVisits() + " из " + describeLimit(link));
        out.println("Создана:     " + DATE_FORMAT.format(link.getCreatedAt()));
        out.println("Истекает:    " + DATE_FORMAT.format(link.getExpiresAt()));
    }

    private void limit(String[] args) {
        requireArgs(args, 3, "limit <code> <N|none>");
        var newLimit = args[2].equalsIgnoreCase("none") ? null : parseLimit(args[2]);
        var link = linkService.updateLimit(args[1], newLimit);
        out.println("Лимит обновлён: " + linkService.display(link.getCode())
                + " -> " + describeLimit(link) + ".");
    }

    private void delete(String[] args) {
        requireArgs(args, 2, "delete <code>");
        linkService.delete(args[1]);
        out.println("Ссылка удалена.");
    }

    private void notifications() {
        var notifications = linkService.pullNotifications();
        if (notifications.isEmpty()) {
            out.println("Уведомлений нет.");
            return;
        }
        out.println("Уведомления (" + notifications.size() + "):");
        for (var notification : notifications) {
            out.println("  [" + DATE_FORMAT.format(notification.getCreatedAt()) + "] "
                    + notification.getType() + ": " + notification.getMessage());
        }
    }

    private void whoami() {
        var user = sessionService.peekUser();
        if (user.isPresent()) {
            out.println("Текущий пользователь: " + user.get());
        } else {
            out.println("Текущий пользователь ещё не определён. Он будет создан при первой команде create.");
        }
    }

    private void login(String[] args) {
        requireArgs(args, 2, "user <uuid>");
        UUID uuid;
        try {
            uuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            throw new LinkShorterException(VALIDATION, "Некорректный UUID: " + args[1]);
        }
        sessionService.login(uuid);
        out.println("Вы вошли как " + uuid + ".");
    }

    private void printHelp() {
        out.println(CliMessages.HELP);
    }

    private void printWelcome() {
        out.println(CliMessages.WELCOME);
    }

    private void requireArgs(String[] args, int minimum, String usage) {
        if (args.length < minimum) {
            throw new LinkShorterException(VALIDATION, "Использование: " + usage);
        }
    }

    private Long parseLimit(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new LinkShorterException(
                    VALIDATION, "Лимит должен быть целым числом, получено: " + raw);
        }
    }

    private String describeLimit(Link link) {
        return link.getMaxVisits() == null ? "без лимита" : String.valueOf(link.getMaxVisits());
    }

    private String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}
