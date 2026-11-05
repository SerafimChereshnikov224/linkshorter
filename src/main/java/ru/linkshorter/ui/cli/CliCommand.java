package ru.linkshorter.ui.cli;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum CliCommand {
    CREATE("create", "c"),
    OPEN("open", "o"),
    LIST("list", "ls"),
    SHOW("show"),
    LIMIT("limit"),
    DELETE("delete", "rm"),
    NOTIFICATIONS("notifications", "notify", "n"),
    WHOAMI("whoami"),
    USER("user", "login"),
    HELP("help", "h", "?"),
    EXIT("exit", "quit", "q");

    private final List<String> aliases;

    CliCommand(String... aliases) {
        this.aliases = List.of(aliases);
    }

    public static Optional<CliCommand> from(String raw) {
        var name = raw.toLowerCase(Locale.ROOT);
        for (var command : values()) {
            if (command.aliases.contains(name)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }
}
