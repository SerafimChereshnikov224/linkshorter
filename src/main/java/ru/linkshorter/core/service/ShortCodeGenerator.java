package ru.linkshorter.core.service;

import java.security.SecureRandom;

public class ShortCodeGenerator {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 32;
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random;
    private final int length;

    public ShortCodeGenerator(int length) {
        this(length, new SecureRandom());
    }

    public ShortCodeGenerator(int length, SecureRandom random) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Длина короткого кода должна быть от "
                    + MIN_LENGTH + " до " + MAX_LENGTH + " символов.");
        }
        this.length = length;
        this.random = random;
    }

    public String generate() {
        var buffer = new char[length];
        for (var i = 0; i < length; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }

    public int length() {
        return length;
    }
}
