package com.app.modules.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A scored item id returned by Gorse recommendation and non-personalized endpoints. */
public record GorseScore(
        @JsonProperty("Id") String id, @JsonProperty("Score") Double score) {}
