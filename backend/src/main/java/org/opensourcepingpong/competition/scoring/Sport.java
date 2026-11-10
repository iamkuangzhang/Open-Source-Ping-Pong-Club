package org.opensourcepingpong.competition.scoring;

/** Sport-specific scoring templates used by E6-D. */
public enum Sport {
    TABLE_TENNIS,
    BADMINTON,
    VOLLEYBALL,
    BASKETBALL,
    FOOTBALL;

    public static Sport parse(String value) {
        if (value == null || value.isBlank()) return TABLE_TENNIS;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return TABLE_TENNIS;
        }
    }
}
