package ru.linkshorter.ui.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.exception.ValidationException;
import ru.linkshorter.core.model.Link;
import ru.linkshorter.core.model.Notification;
import ru.linkshorter.core.port.BrowserLauncher;
import ru.linkshorter.core.service.LinkService;

public class Cli {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final LinkService linkService;
    private final BrowserLauncher browserLauncher;
    private final PrintStream out;
    private final BufferedReader in;
    private boolean running = true;

    public Cli(LinkService linkService, BrowserLauncher browserLauncher, InputStream in, PrintStream out) {
        this.linkService = linkService;
        this.browserLauncher = browserLauncher;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    public void run() {
        printWelcome();
        while (running) {
            out.print("> ");
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Ошибка чтения ввода: " + e.getMessage());
                return;
            }
            if (line == null) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            execute(line.trim().split("\\s+"));
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
        } catch (LinkShorterException e) {
            out.println("Ошибка: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            out.println("Непредвиденная ошибка: " + e.getMessage());
            return 1;
        }
    }

    private void dispatch(String[] args) {
        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "create", "c" -> create(args);
            case "open", "o" -> open(args);
            case "list", "ls" -> list();
            case "show" -> show(args);
            case "limit" -> limit(args);
            case "delete", "rm" -> delete(args);
            case "notifications", "notify", "n" -> notifications();
            case "whoami" -> whoami();
            case "user", "login" -> login(args);
            case "help", "h", "?" -> printHelp();
            case "exit", "quit", "q" -> running = false;
            default -> out.println(
                    "Неизвестная команда '" + args[0] + "'. Введите 'help' для списка команд.");
        }
    }

    private void create(String[] args) {
        requireArgs(args, 2, "create <url> [--limit N]");
        String url = args[1];
        Long maxVisits = null;
        for (int i = 2; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--limit") || option.equals("-l")) {
                if (i + 1 >= args.length) {
                    throw new ValidationException("После " + option + " нужно указать число.");
                }
                maxVisits = parseLimit(args[++i]);
            } else {
                throw new ValidationException(
                        "Неизвестный параметр '" + option + "'. Используйте: create <url> [--limit N]");
            }
        }

        Link link = linkService.create(url, maxVisits);
        out.println("Ссылка создана: " + linkService.display(link.getCode()));
        out.println("  Оригинал:   " + link.getOriginalUrl());
        out.println("  Владелец:   " + link.getOwnerUuid());
        out.println("  Лимит:      " + describeLimit(link));
        out.println("  Действует:  до " + DATE_FORMAT.format(link.getExpiresAt()));
    }

    private void open(String[] args) {
        requireArgs(args, 2, "open <code>");
        Link link = linkService.open(args[1]);
        out.println("Переход " + link.getVisits() + "/" + describeLimit(link)
                + ": открываю " + link.getOriginalUrl());
        browserLauncher.open(link.getOriginalUrl());
        if (link.isLimitReached()) {
            out.println("Внимание: это был последний доступный переход по ссылке.");
        }
    }

    private void list() {
        List<Link> links = linkService.listMine();
        long unread = linkService.unreadNotificationCount();
        if (links.isEmpty()) {
            out.println("У вас пока нет ссылок. Создайте первую: create <url> [--limit N]");
        } else {
            out.printf("%-10s %-14s %-10s %-20s %s%n", "КОД", "СТАТУС", "ПЕРЕХОДЫ", "ИСТЕКАЕТ", "АДРЕС");
            for (Link link : links) {
                out.printf(
                        "%-10s %-14s %-10s %-20s %s%n",
                        link.getCode(),
                        link.status(java.time.Instant.now()),
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
        Link link = linkService.findOwned(args[1]);
        out.println("Код:         " + linkService.display(link.getCode()));
        out.println("Оригинал:    " + link.getOriginalUrl());
        out.println("Владелец:    " + link.getOwnerUuid());
        out.println("Статус:      " + link.status(java.time.Instant.now()));
        out.println("Переходы:    " + link.getVisits() + " из " + describeLimit(link));
        out.println("Создана:     " + DATE_FORMAT.format(link.getCreatedAt()));
        out.println("Истекает:    " + DATE_FORMAT.format(link.getExpiresAt()));
    }

    private void limit(String[] args) {
        requireArgs(args, 3, "limit <code> <N|none>");
        Long newLimit = args[2].equalsIgnoreCase("none") ? null : parseLimit(args[2]);
        Link link = linkService.updateLimit(args[1], newLimit);
        out.println("Лимит обновлён: " + linkService.display(link.getCode())
                + " -> " + describeLimit(link) + ".");
    }

    private void delete(String[] args) {
        requireArgs(args, 2, "delete <code>");
        linkService.delete(args[1]);
        out.println("Ссылка удалена.");
    }

    private void notifications() {
        List<Notification> notifications = linkService.pullNotifications();
        if (notifications.isEmpty()) {
            out.println("Уведомлений нет.");
            return;
        }
        out.println("Уведомления (" + notifications.size() + "):");
        for (Notification notification : notifications) {
            out.println("  [" + DATE_FORMAT.format(notification.getCreatedAt()) + "] "
                    + notification.getType() + ": " + notification.getMessage());
        }
    }

    private void whoami() {
        Optional<UUID> user = linkService.peekUser();
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
            throw new ValidationException("Некорректный UUID: " + args[1]);
        }
        linkService.login(uuid);
        out.println("Вы вошли как " + uuid + ".");
    }

    private void printHelp() {
        out.println("""
                Команды:
                  create <url> [--limit N]  сократить ссылку (N — максимальное число переходов)
                  open <code>               перейти по короткой ссылке в браузере
                  list                      показать мои ссылки
                  show <code>               подробности ссылки
                  limit <code> <N|none>     изменить лимит переходов (none — без ограничения)
                  delete <code>             удалить ссылку
                  notifications             показать уведомления
                  whoami                    показать текущий UUID
                  user <uuid>               войти под указанным UUID
                  help                      этот список
                  exit                      выход

                Примеры:
                  create https://ru.stackoverflow.com --limit 5
                  create https://www.baeldung.com/java-9-http-client
                  open sho.rt/aB3dE9x
                  limit sho.rt/aB3dE9x 20
                """);
    }

    private void printWelcome() {
        out.println("""
                ==========================================
                  LinkShorter — консольный сервис ссылок
                ==========================================
                Введите 'help' для списка команд.
                """);
    }

    private void requireArgs(String[] args, int minimum, String usage) {
        if (args.length < minimum) {
            throw new ValidationException("Использование: " + usage);
        }
    }

    private Long parseLimit(String raw) {
        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ValidationException("Лимит должен быть целым числом, получено: " + raw);
        }
        return value;
    }

    private String describeLimit(Link link) {
        if (link.getMaxVisits() == null) {
            return "без лимита";
        }
        return String.valueOf(link.getMaxVisits());
    }

    private String shorten(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
