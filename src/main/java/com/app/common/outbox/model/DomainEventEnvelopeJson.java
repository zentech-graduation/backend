package com.app.common.outbox.model;

import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON codec for the durable domain event envelope stored and published by the outbox.
 *
 * <p>Deliberately standalone rather than the Spring-managed mapper: this is a durable storage
 * format, and a row written by one release must stay readable by the next regardless of how the
 * HTTP layer's mapper is later reconfigured. It does mirror the HTTP layer on one point - writing
 * every timestamp in UTC - because an event's {@code data} is republished verbatim to WebSocket
 * subscribers, so a divergence here would surface to clients as the same field rendering one way
 * over REST and another over the socket.
 */
public final class DomainEventEnvelopeJson {

    private static final ObjectMapper JSON_MAPPER =
            JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                    .enable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
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
