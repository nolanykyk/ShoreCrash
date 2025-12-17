package com.shorecrash.util;

import java.util.Locale;

public final class AmountParser {
    private AmountParser() {}

    public static Double parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String value = input.trim().toLowerCase(Locale.US);
        double multiplier = 1.0;
        char last = value.charAt(value.length() - 1);
        if (last == 'k' || last == 'm' || last == 'b') {
            value = value.substring(0, value.length() - 1);
            if (last == 'k') multiplier = 1_000d;
            if (last == 'm') multiplier = 1_000_000d;
            if (last == 'b') multiplier = 1_000_000_000d;
        }
        try {
            double base = Double.parseDouble(value);
            return base * multiplier;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
