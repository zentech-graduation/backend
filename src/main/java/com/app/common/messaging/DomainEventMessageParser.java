package com.app.common.messaging;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.common.outbox.model.DomainEventEnvelopeJson;

/** Parses RabbitMQ message bodies into durable domain event envelopes. */
@Component
public class DomainEventMessageParser {

    public DomainEventEnvelope parse(Message message) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            throw new PermanentMessageException("Message body is empty");
        }
        try {
            return DomainEventEnvelopeJson.read(
                    new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            throw new PermanentMessageException(
                    "Message body is not a readable event envelope", ex);
        }
    }
}
