package com.app.common.devseed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Triggers development data seeding shortly after startup.
 *
 * <p>Active only under the {@code dev} profile and only when {@code SEED_DATA=true}, so it never
 * runs in production and is opt-in even locally. The work runs on a daemon thread after a short
 * delay, off the startup path, so a slow or failing seed never blocks or fails application startup.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "SEED_DATA", havingValue = "true")
@RequiredArgsConstructor
public class DevDataSeeder {

    private static final long START_DELAY_MS = 10_000L;

    private final DevDataSeedService seedService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread worker = new Thread(this::runSeed, "dev-data-seeder");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSeed() {
        try {
            Thread.sleep(START_DELAY_MS);
            DevSeedResult result = seedService.seed();
            if (result.skipped()) {
                log.info("[dev-seed] database already seeded; nothing to do");
                return;
            }
            log.info("[dev-seed] complete: {}", result.summary());
            log.info(
                    "[dev-seed] REVIEWER account -> username '{}', email '{}', password '{}',"
                            + " follows {} accounts",
                    result.reviewerUsername(),
                    result.reviewerEmail(),
                    result.reviewerPassword(),
                    result.reviewerFollows());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn(
                    "[dev-seed] seeding failed (development only, ignored): {}", e.getMessage(), e);
        }
    }
}
