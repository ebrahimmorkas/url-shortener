package com.ebrahimmorkas.shortener.common;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("Short link '%s' does not exist".formatted(code));
    }
}
