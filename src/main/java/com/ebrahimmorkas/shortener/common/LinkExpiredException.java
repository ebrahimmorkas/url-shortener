package com.ebrahimmorkas.shortener.common;

public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String code) {
        super("Short link '%s' has expired".formatted(code));
    }
}
