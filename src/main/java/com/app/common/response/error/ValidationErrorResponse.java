package com.app.common.response.error;

import java.util.Map;

import com.app.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The failure envelope a {@code VALIDATION_ERROR} rejection returns, with {@code data} typed.
 *
 * <p>{@code data} maps a field name to the message describing why that field was rejected, which is
 * what lets a client attach each message to its own input rather than showing one summary line.
 *
 * <p>Not referenced from an individual operation's 400, because a 400 on most operations covers
 * more than bean validation: a malformed cursor, an out-of-range page size and an unsupported query
 * parameter all answer 400 with no {@code data} at all. Naming this envelope there would tell a
 * generated client a payload always arrives when usually none does. It is registered as a named
 * component instead, so the type exists for a client that switches on the code.
 *
 * <p>Never instantiated: it is a type token for the schema generator.
 */
@Schema(
        name = "ValidationErrorResponse",
        description =
                "Failure envelope whose data maps each rejected field name to its message; the"
                        + " shape returned whenever code is VALIDATION_ERROR")
public class ValidationErrorResponse extends ApiResponse<Map<String, String>> {}
