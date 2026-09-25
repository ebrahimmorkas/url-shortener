package com.ebrahimmorkas.shortener.link;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void codesAreSevenUrlSafeCharacters() {
        LongStream.of(1_000, 123_456_789, 3_521_614_606_207L).forEach(id ->
                assertThat(generator.generate(id)).hasSize(7).matches("[0-9A-Za-z]{7}"));
    }

    @Test
    void distinctIdsNeverCollide() {
        Set<String> codes = new HashSet<>();
        LongStream.range(1_000, 201_000).forEach(id -> codes.add(generator.generate(id)));

        assertThat(codes).hasSize(200_000);
    }

    @Test
    void consecutiveIdsDoNotProduceSequentialCodes() {
        String first = generator.generate(1_000);
        String second = generator.generate(1_001);

        assertThat(commonPrefixLength(first, second)).isLessThan(3);
    }

    @Test
    void generationIsDeterministic() {
        assertThat(generator.generate(42_000)).isEqualTo(generator.generate(42_000));
    }

    @Test
    void rejectsIdsOutsideTheCodeSpace() {
        assertThatThrownBy(() -> generator.generate(3_521_614_606_208L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void base62EncodingIsPositional() {
        assertThat(ShortCodeGenerator.toBase62(0)).isEqualTo("0000000");
        assertThat(ShortCodeGenerator.toBase62(61)).isEqualTo("000000z");
        assertThat(ShortCodeGenerator.toBase62(62)).isEqualTo("0000010");
    }

    private static int commonPrefixLength(String a, String b) {
        int i = 0;
        while (i < a.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }
}
