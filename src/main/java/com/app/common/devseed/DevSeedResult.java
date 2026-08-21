package com.app.common.devseed;

/** Outcome of a development seeding run, used only to report to the log. */
public record DevSeedResult(
        boolean skipped,
        String summary,
        String reviewerUsername,
        String reviewerEmail,
        String reviewerPassword,
        int reviewerFollows) {

    public static DevSeedResult alreadySeeded() {
        return new DevSeedResult(true, "", null, null, null, 0);
    }
}
