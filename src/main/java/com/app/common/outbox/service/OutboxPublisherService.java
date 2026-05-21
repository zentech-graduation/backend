package com.app.common.outbox.service;

/** Service API for publishing due outbox events to RabbitMQ. */
public interface OutboxPublisherService {

    /**
     * Publishes due outbox events and updates each row according to the broker confirm outcome.
     *
     * @return number of outbox rows attempted in this run
     */
    int publishDueEvents();
}
