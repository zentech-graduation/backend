package com.app.modules.mail.config.noop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every {@link SentMail} the {@code noop} transport has captured.
 *
 * <p>Constructed fresh per Spring context by {@link NoopMailConfig}, so no test resets it and no
 * state leaks between contexts that don't share one.
 */
public class SentMailRecorder {

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();

    public void record(SentMail mail) {
        sent.add(mail);
    }

    public List<SentMail> sent() {
        return List.copyOf(sent);
    }
}
