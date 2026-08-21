package com.app.modules.recommendation.client.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A user-item feedback row in Gorse wire format. */
public record GorseFeedback(
        @JsonProperty("FeedbackType") String feedbackType,
        @JsonProperty("UserId") String userId,
        @JsonProperty("ItemId") String itemId,
        @JsonProperty("Timestamp") OffsetDateTime timestamp) {}
