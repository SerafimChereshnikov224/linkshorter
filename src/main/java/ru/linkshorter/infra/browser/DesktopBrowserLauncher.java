package ru.linkshorter.infra.browser;

import static ru.linkshorter.core.exception.LinkShorterException.Type.UNAVAILABLE;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import ru.linkshorter.core.browser.BrowserLauncher;
import ru.linkshorter.core.exception.LinkShorterException;

public class DesktopBrowserLauncher implements BrowserLauncher {

    @Override
    public void open(String url) {
        if (!Desktop.isDesktopSupported()) {
            throw new LinkShorterException(
                    UNAVAILABLE,
                    "Открытие браузера недоступно в этой среде. Ссылка: " + url);
        }
        var desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new LinkShorterException(
                    UNAVAILABLE,
                    "Система не поддерживает действие BROWSE. Ссылка: " + url);
        }
        try {
            desktop.browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            throw new LinkShorterException(
                    UNAVAILABLE,
                    "Не удалось открыть браузер: " + e.getMessage(),
                    e);
        }
    }
}
