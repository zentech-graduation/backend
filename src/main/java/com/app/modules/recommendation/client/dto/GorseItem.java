package com.app.modules.recommendation.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An item row in Gorse wire format; labels carry topic signals for tag-based neighbors. */
public record GorseItem(
        @JsonProperty("ItemId") String itemId,
        @JsonProperty("IsHidden") boolean hidden,
        @JsonProperty("Categories") List<String> categories,
        @JsonProperty("Labels") List<String> labels,
        @JsonProperty("Timestamp") OffsetDateTime timestamp,
        @JsonProperty("Comment") String comment) {}
