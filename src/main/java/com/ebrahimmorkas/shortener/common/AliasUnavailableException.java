package com.ebrahimmorkas.shortener.common;

public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String alias, String reason) {
        super("Alias '%s' is not available: %s".formatted(alias, reason));
    }
}
