package ru.linkshorter.core.exception;

public class LinkShorterException extends RuntimeException {

    public enum Type {
        VALIDATION,
        NOT_FOUND,
        ACCESS_DENIED,
        UNAVAILABLE,
        STORAGE
    }

    private final Type type;

    public LinkShorterException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public LinkShorterException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }
}
