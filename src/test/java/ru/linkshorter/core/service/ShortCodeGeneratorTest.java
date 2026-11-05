package ru.linkshorter.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    @Test
    void generatesCodeOfConfiguredLength() {
        ShortCodeGenerator generator = new ShortCodeGenerator(7);

        String code = generator.generate();

        assertThat(code).hasSize(7).matches("[0-9A-Za-z]{7}");
    }

    @Test
    void generatesUniqueCodes() {
        ShortCodeGenerator generator = new ShortCodeGenerator(7);
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }

        assertThat(codes).hasSize(1000);
    }

    @Test
    void rejectsInvalidLength() {
        assertThatThrownBy(() -> new ShortCodeGenerator(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShortCodeGenerator(33))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
