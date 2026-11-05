package ru.linkshorter.core.exception;

import ru.linkshorter.core.model.LinkStatus;

public class LinkUnavailableException extends LinkShorterException {

    private final String code;
    private final LinkStatus status;

    public LinkUnavailableException(String code, LinkStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public LinkStatus getStatus() {
        return status;
    }
}
