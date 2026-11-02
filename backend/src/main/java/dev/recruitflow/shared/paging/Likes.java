package dev.recruitflow.shared.paging;

import java.util.Locale;

/** Builds a case-insensitive LIKE pattern with user-supplied wildcards neutralised (use with escape char '\\'). */
public final class Likes {

    public static final char ESCAPE = '\\';

    private Likes() {}

    public static String contains(String raw) {
        String escaped = raw.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
