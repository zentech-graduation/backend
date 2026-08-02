package com.app.common.pagination;

import java.util.UUID;

/**
 * Decoded keyset cursor position.
 *
 * <p>Pairs the primary sort value, expressed as epoch microseconds, with the row id that breaks
 * ties on equal sort values. Every cursor-paginated endpoint anchors its next page on this tuple.
 *
 * @param sortValueMicros primary sort value as microseconds since the epoch
 * @param id row id that uniquely orders rows sharing {@code sortValueMicros}
 */
public record Cursor(long sortValueMicros, UUID id) {}
