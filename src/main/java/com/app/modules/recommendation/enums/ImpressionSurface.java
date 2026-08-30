package com.app.modules.recommendation.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The surface an impression was reported from.
 *
 * <p>Recorded so impression behaviour can be analysed per surface later; the recommender itself
 * treats every surface identically today.
 */
public enum ImpressionSurface {
    FEED,
    EXPLORE,
    SEARCH;

    @JsonCreator
    public static ImpressionSurface fromJson(String value) {
        return value == null || value.isBlank()
                ? null
                : ImpressionSurface.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
