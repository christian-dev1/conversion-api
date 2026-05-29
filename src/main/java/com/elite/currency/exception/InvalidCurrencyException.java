package com.elite.currency.exception;

public class InvalidCurrencyException extends RuntimeException {

    private final String currencyCode;

    public InvalidCurrencyException(String currencyCode) {
        super("Unsupported or unknown currency code: '" + currencyCode + "'. "
              + "Please provide a valid ISO 4217 three-letter code (e.g. USD, EUR, XAF).");
        this.currencyCode = currencyCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
