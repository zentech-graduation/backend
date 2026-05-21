package com.app.common.outbox.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** JSON codec for the durable domain event envelope stored and published by the outbox. */
public final class DomainEventEnvelopeJson {

    private static final ObjectMapper JSON_MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build();

    private DomainEventEnvelopeJson() {}

    public static String write(DomainEventEnvelope payload) {
        try {
            return JSON_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Outbox event payload must be JSON serializable", ex);
        }
    }

    public static DomainEventEnvelope read(String payload) {
        try {
            return JSON_MAPPER.readValue(payload, DomainEventEnvelope.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored outbox event payload is not readable", ex);
        }
    }
}
