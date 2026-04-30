package com.app.modules.auth.exception;

public class TokenAlreadyUsedException extends RuntimeException {

    public TokenAlreadyUsedException(String message) {
        super(message);
    }
}
