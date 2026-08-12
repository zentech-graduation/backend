package com.app.modules.post.validation;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.CursorScope;
import com.app.modules.post.enums.PostType;

/**
 * A validated, normalized set of {@code post_type} values used to filter a profile's posts.
 *
 * <p>Normalization is what makes the set safe to bind into a cursor scope: the values are held in
 * an {@link EnumSet}, so they are deduplicated and always iterate in declaration order regardless
 * of the order the client sent them. {@code type=video&type=image} and {@code
 * type=image&type=video} therefore produce one identical scope, and a cursor issued under either is
 * accepted by the other.
 *
 * <p>An empty filter is the unfiltered case and deliberately maps back to the bare {@link
 * CursorScope#POST_USER_POSTS} tag, so cursors issued before this filter existed keep working.
 */
public record PostTypeFilter(Set<PostType> types) {

    private static final String ACCEPTED_VALUES =
            Arrays.stream(PostType.values())
                    .map(PostType::toJson)
                    .collect(Collectors.joining(", "));

    public static PostTypeFilter empty() {
        return new PostTypeFilter(EnumSet.noneOf(PostType.class));
    }

    /**
     * Parses and validates raw {@code type} query parameter values.
     *
     * <p>An unrecognized value is rejected rather than ignored, and the message names every
     * accepted value: silently dropping an unknown filter is indistinguishable to a client from a
     * filter that matched everything, which is the defect this parameter exists to avoid repeating.
     *
     * @param raw values as received from the query string; null, empty, and all-blank all mean no
     *     filter
     * @return a normalized filter, empty when no usable value was supplied
     * @throws AppException with {@link ApiErrorCode#BAD_REQUEST} when a value is not a post type
     */
    public static PostTypeFilter parse(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Set<PostType> parsed = EnumSet.noneOf(PostType.class);
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            parsed.add(parseOne(value.trim()));
        }
        return new PostTypeFilter(parsed);
    }

    private static PostType parseOne(String value) {
        try {
            return PostType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST,
                    "Unsupported post type '" + value + "'. Accepted values: " + ACCEPTED_VALUES);
        }
    }

    public boolean isEmpty() {
        return types.isEmpty();
    }

    /**
     * The filter as a comma-delimited list of lowercase type names in normalized order.
     *
     * <p>Consumed by the repository as a single bind parameter that Postgres expands with {@code
     * string_to_array(...)::post_type[]}. The cast lands on the parameter rather than on the
     * column, which keeps the comparison enum-to-enum: casting {@code post_type} to text instead
     * discards the column's statistics and was measured to turn an index scan into a bitmap heap
     * scan plus a sort.
     *
     * @return comma-delimited type names, empty string when the filter is empty
     */
    public String asDelimitedTypes() {
        return types.stream().map(PostType::toJson).collect(Collectors.joining(","));
    }

    /**
     * The cursor scope tag for this filter.
     *
     * <p>Binding the filter into the scope is what stops a cursor issued under one filter being
     * replayed under another. The ordering is identical either way, so a replay would succeed
     * silently while omitting every row the other filter excludes that sits before the cursor
     * position.
     *
     * @return the unfiltered scope tag when empty, otherwise that tag suffixed with the normalized
     *     type names
     */
    public String cursorScope() {
        return isEmpty()
                ? CursorScope.POST_USER_POSTS
                : CursorScope.POST_USER_POSTS
                        + "."
                        + types.stream().map(PostType::toJson).collect(Collectors.joining("."));
    }
}
