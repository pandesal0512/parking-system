package com.mabini.parking.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Plate numbers are written many ways ("abc 1234", "ABC-1234"). We store and compare
 * them in ONE form: uppercase, no spaces, no dashes.
 */
public final class Identifiers {

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9]{2,12}$");

    private Identifiers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    public static boolean isValid(String normalized) {
        return normalized != null && VALID.matcher(normalized).matches();
    }
}
