/**
 * Dev-database seed pipeline: populates a local PostgreSQL instance with a full, internally
 * consistent social-network dataset (users, posts, comments, stories, messages, moderation history,
 * notifications, analytics) for frontend UI review and manual QA.
 *
 * <p>Runs once, a few seconds after application startup, only under the {@code dev} Spring profile
 * and only when {@code SEED_DATA=true} is set ({@code com.app.common.seed.SeedRunner}); it never
 * runs in production and refuses to run against anything but a local database.
 *
 * <p>See {@code src/main/resources/seed/README.md} for the full volume table, the QA account list,
 * the content-file map, and how to re-provision seed media.
 */
package com.app.common.seed;
