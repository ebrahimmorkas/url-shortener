package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.common.AliasUnavailableException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Rules that keep custom aliases from clashing with anything else.
 *
 * <p>Generated codes are always exactly 7 alphanumeric characters, so aliases of that exact
 * shape are reserved. That makes a collision between an alias and a future generated code
 * impossible by construction, rather than something detected at insert time.
 */
@Component
public class AliasPolicy {

    private static final Set<String> RESERVED = Set.of("actuator", "swagger-ui", "api-docs", "admin", "login");

    public void check(String alias) {
        if (alias.matches("[0-9A-Za-z]{" + ShortCodeGenerator.CODE_LENGTH + "}")) {
            throw new AliasUnavailableException(alias,
                    "7-character alphanumeric aliases are reserved for generated codes; add '-' or '_' or change the length");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new AliasUnavailableException(alias, "this word is reserved");
        }
    }
}
