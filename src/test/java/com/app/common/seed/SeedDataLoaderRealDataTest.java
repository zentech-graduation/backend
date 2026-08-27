package com.app.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.app.common.seed.loader.SeedContent;
import com.app.common.seed.loader.SeedDataLoader;

/**
 * Loads the real seed JSON files under {@code src/main/resources/seed/} to prove Task 1/2's content
 * is internally consistent by this loader's rules. Not a fixture test — a failure here means the
 * actual seed data is broken, not the loader.
 */
class SeedDataLoaderRealDataTest {

    @Test
    void load_realSeedContent_loadsSuccessfully() {
        SeedContent content = new SeedDataLoader().load();

        assertThat(content.personas()).hasSize(10);
        assertThat(content.users()).hasSize(90);
        assertThat(content.hashtags()).hasSize(152);
        assertThat(content.posts()).hasSize(722);
        assertThat(content.conversations()).hasSize(60);
        assertThat(content.moderationCases()).hasSize(6);
        assertThat(content.supplementaryModerationActions()).hasSize(153);
        assertThat(content.supplementaryModerationReports()).hasSize(27);
        assertThat(content.mediaManifest()).hasSize(120 + 15 + 95 + 25);
    }
}
