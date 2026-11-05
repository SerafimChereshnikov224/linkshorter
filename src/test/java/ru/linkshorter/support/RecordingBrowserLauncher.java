package ru.linkshorter.support;

import java.util.ArrayList;
import java.util.List;
import ru.linkshorter.core.port.BrowserLauncher;

public class RecordingBrowserLauncher implements BrowserLauncher {

    private final List<String> openedUrls = new ArrayList<>();

    @Override
    public void open(String url) {
        openedUrls.add(url);
    }

    public List<String> openedUrls() {
        return openedUrls;
    }
}
