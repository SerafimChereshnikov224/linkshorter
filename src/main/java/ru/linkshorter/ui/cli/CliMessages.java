package ru.linkshorter.ui.cli;

public final class CliMessages {

    public static final String WELCOME = """
            ==========================================
              LinkShorter — консольный сервис ссылок
            ==========================================
            Введите 'help' для списка команд.
            """;

    public static final String HELP = """
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
            """;
}
