package com.chrisdev.supporthub.user.api;

public final class PasswordRules {

    /**
     * Al menos una letra y un dígito. El largo se valida con @Size.
     */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String MESSAGE = "must contain at least one letter and one digit";

    private PasswordRules() {
    }
}
