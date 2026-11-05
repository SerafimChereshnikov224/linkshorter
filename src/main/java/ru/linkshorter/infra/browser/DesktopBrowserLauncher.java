package ru.linkshorter.infra.browser;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import ru.linkshorter.core.exception.LinkShorterException;
import ru.linkshorter.core.port.BrowserLauncher;

public class DesktopBrowserLauncher implements BrowserLauncher {

    @Override
    public void open(String url) {
        if (!Desktop.isDesktopSupported()) {
            throw new LinkShorterException(
                    "Открытие браузера недоступно в этой среде. Ссылка: " + url);
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new LinkShorterException(
                    "Система не поддерживает действие BROWSE. Ссылка: " + url);
        }
        try {
            desktop.browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            throw new LinkShorterException("Не удалось открыть браузер: " + e.getMessage(), e);
        }
    }
}
