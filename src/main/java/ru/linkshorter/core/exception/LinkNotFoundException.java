package ru.linkshorter.core.exception;

public class LinkNotFoundException extends LinkShorterException {

    public LinkNotFoundException(String code) {
        super("Ссылка с кодом '" + code + "' не найдена.");
    }
}
