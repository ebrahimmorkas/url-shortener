package com.ebrahimmorkas.shortener.link;

import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * Turns a database sequence value into a 7-character Base62 code.
 *
 * <p>Encoding the raw id would produce guessable, sequential codes (…aB, …aC), letting anyone
 * enumerate every link. Instead the id is first multiplied by a constant that is coprime with
 * 62^7, modulo 62^7. That is a bijection on [0, 62^7): distinct ids always map to distinct codes
 * (no collisions, no retries, no lookups) while consecutive ids land far apart. Capacity is
 * 62^7 ≈ 3.5 trillion links.
 */
@Component
public class ShortCodeGenerator {

    static final int CODE_LENGTH = 7;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigInteger SPACE = BigInteger.valueOf(62).pow(CODE_LENGTH);
    /** Odd and not divisible by 31, hence coprime with 62^7 = 2^7 · 31^7. */
    private static final BigInteger MULTIPLIER = BigInteger.valueOf(1_580_030_173L);

    public String generate(long id) {
        if (id < 0 || BigInteger.valueOf(id).compareTo(SPACE) >= 0) {
            throw new IllegalArgumentException("id out of range: " + id);
        }
        long scrambled = BigInteger.valueOf(id).multiply(MULTIPLIER).mod(SPACE).longValueExact();
        return toBase62(scrambled);
    }

    static String toBase62(long value) {
        char[] code = new char[CODE_LENGTH];
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            code[i] = ALPHABET.charAt((int) (value % 62));
            value /= 62;
        }
        return new String(code);
    }
}
