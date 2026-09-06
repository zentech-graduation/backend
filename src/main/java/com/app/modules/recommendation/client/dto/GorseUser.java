package com.app.modules.recommendation.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A user row in Gorse wire format. */
public record GorseUser(
        @JsonProperty("UserId") String userId,
        @JsonProperty("Labels") List<String> labels,
        @JsonProperty("Comment") String comment) {}
