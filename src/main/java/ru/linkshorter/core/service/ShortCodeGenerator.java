package ru.linkshorter.core.service;

import java.security.SecureRandom;

public class ShortCodeGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random;
    private final int length;

    public ShortCodeGenerator(int length) {
        if (length < 4 || length > 32) {
            throw new IllegalArgumentException("Длина короткого кода должна быть от 4 до 32 символов.");
        }
        this.length = length;
        this.random = new SecureRandom();
    }

    public ShortCodeGenerator(int length, SecureRandom random) {
        if (length < 4 || length > 32) {
            throw new IllegalArgumentException("Длина короткого кода должна быть от 4 до 32 символов.");
        }
        this.length = length;
        this.random = random;
    }

    public String generate() {
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }

    public int length() {
        return length;
    }
}
