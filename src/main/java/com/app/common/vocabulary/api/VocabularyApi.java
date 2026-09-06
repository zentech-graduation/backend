package com.app.common.vocabulary.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.response.ApiResponse;
import com.app.common.vocabulary.dto.response.VocabularyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for the read-only configuration surface.
 *
 * <p>The API requires a client to send keys drawn from three configuration tables, and until now
 * nothing exposed them. A client could learn the accepted values from the enum sets the document
 * declares, but not the label to render, the order to render them in, which report types a reason
 * applies to, or whether an administrator has switched one off. The last of those is runtime state:
 * without it a client keeps offering a retired reason and the user meets a rejection for something
 * the interface said was valid.
 *
 * <p>Readable by any authenticated caller rather than administrators only, because an ordinary user
 * submitting a report needs the same reason list a moderator needs when issuing a warning.
 */
@Tag(
        name = "Configuration",
        description = "Read-only display vocabularies behind the API's closed enum sets")
@RequestMapping(ApiConstants.Config.ROOT)
public interface VocabularyApi {

    /** Returns every display vocabulary in one response. */
    @Operation(
            summary = "Get every display vocabulary",
            description =
                    "Returns report reasons, notification types and moderation action types, each"
                            + " with its display metadata and its enabled flag. One call rather than"
                            + " three, because a client loads this once at start-up and caches it."
                            + " These are small closed sets, so the response is not paginated."
                            + " Disabled rows are returned rather than filtered out, so a client can"
                            + " render a retired reason as unavailable instead of having it vanish"
                            + " between page loads. A report reason's appliesTo list is empty when"
                            + " the reason applies to every report type, never when it applies to"
                            + " none, so a client filtering reasons by the type being reported must"
                            + " treat an empty list as a match. Any authenticated caller may read"
                            + " it.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Every vocabulary, including disabled rows"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Config.VOCABULARIES)
    ResponseEntity<ApiResponse<VocabularyResponse>> getVocabularies();
}
