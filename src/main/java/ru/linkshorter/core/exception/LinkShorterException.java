package ru.linkshorter.core.exception;

public class LinkShorterException extends RuntimeException {

    public LinkShorterException(String message) {
        super(message);
    }

    public LinkShorterException(String message, Throwable cause) {
        super(message, cause);
    }
}
