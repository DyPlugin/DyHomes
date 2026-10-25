package dev.dyhomes.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** Home and warp names: letters (any alphabet), digits, '_' and '-', 1-32 characters, compared without case. */
public final class Names {
    public static final String DEFAULT_HOME = "home";
    private static final Pattern VALID = Pattern.compile("[\\p{L}\\p{N}_-]{1,32}");

    private Names() {
    }

    public static boolean valid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
