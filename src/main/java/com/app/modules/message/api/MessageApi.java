package com.app.modules.message.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.config.openapi.MalformedBodyErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.message.dto.request.AddParticipantsRequest;
import com.app.modules.message.dto.request.CreateDirectConversationRequest;
import com.app.modules.message.dto.request.CreateGroupRequest;
import com.app.modules.message.dto.request.UpdateGroupRequest;
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.ParticipantResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for conversation creation, membership, and group management. */
@Tag(name = "Messages", description = "1-1 and group conversation lifecycle and membership")
public interface MessageApi {

    @Operation(
            summary = "Start or reuse a 1-1 conversation",
            description =
                    "Starts a 1-1 conversation with the target user, or returns the existing one"
                            + " between the same two users. Requires authentication.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Conversation created or reused"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Blocked, or target does not accept message requests from the caller",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target user not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @MalformedBodyErrorResponses
    @AuthenticationRequiredResponse
    @PostMapping(ApiConstants.Messages.ROOT)
    ResponseEntity<ApiResponse<ConversationResponse>> createDirectConversation(
            @Valid @RequestBody CreateDirectConversationRequest request);

    @Operation(
            summary = "Create a group conversation",
            description =
                    "Creates a group conversation with the caller as its first admin. Requires"
                            + " authentication.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Group conversation created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Group chat is disabled",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid participant list",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PostMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.GROUP)
    ResponseEntity<ApiResponse<ConversationResponse>> createGroupConversation(
            @Valid @RequestBody CreateGroupRequest request);

    @Operation(
            summary = "List the caller's conversations",
            description =
                    "Cursor-paginated list of the caller's active conversations, newest activity"
                            + " first, with unread counts. Requires authentication.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of conversation summaries")
    })
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Messages.ROOT)
    ResponseEntity<ApiResponse<CursorPageResponse<ConversationSummaryResponse>>>
            listMyConversations(
                    @Parameter(description = "Opaque cursor from the previous page")
                            @RequestParam(value = "cursor", required = false)
                            String cursor,
                    @Parameter(description = "Page size, 1-100, default 20")
                            @RequestParam(value = "limit", defaultValue = "20")
                            @Min(1)
                            @Max(100)
                            int limit);

    @Operation(
            summary = "Get a conversation's detail",
            description =
                    "Returns one conversation's detail, including active and former participants."
                            + " Requires authentication as an active participant.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Conversation detail"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active participant",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Conversation not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.BY_ID)
    ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @PathVariable("conversationId") UUID conversationId);

    @Operation(
            summary = "Rename a group or change its avatar",
            description =
                    "Updates a group conversation's name and/or avatar; fields left null are"
                            + " unchanged. Requires authentication as an active group admin.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Group updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active group admin",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Conversation is not a group",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @MalformedBodyErrorResponses
    @AuthenticationRequiredResponse
    @PatchMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.BY_ID)
    ResponseEntity<ApiResponse<ConversationResponse>> updateGroup(
            @PathVariable("conversationId") UUID conversationId,
            @Valid @RequestBody UpdateGroupRequest request);

    @Operation(
            summary = "List a conversation's members",
            description =
                    "Lists a conversation's active and former members. Requires authentication as"
                            + " an active participant.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Member list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active participant",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.PARTICIPANTS)
    ResponseEntity<ApiResponse<List<ParticipantResponse>>> listParticipants(
            @PathVariable("conversationId") UUID conversationId);

    @Operation(
            summary = "Add members to a group",
            description =
                    "Adds members to a group conversation; an already-active member is a no-op and"
                            + " a former member is reactivated. Requires authentication as an"
                            + " active group admin.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Members added"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active group admin",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Conversation is not a group",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @MalformedBodyErrorResponses
    @AuthenticationRequiredResponse
    @PostMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.PARTICIPANTS)
    ResponseEntity<ApiResponse<Void>> addParticipants(
            @PathVariable("conversationId") UUID conversationId,
            @Valid @RequestBody AddParticipantsRequest request);

    @Operation(
            summary = "Remove a member from a group",
            description =
                    "Removes an active member from a group conversation by setting their departure"
                            + " timestamp; membership history is preserved. Requires"
                            + " authentication as an active group admin.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Member removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller is not an active group admin",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Target is not an active member",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @DeleteMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.PARTICIPANT_BY_ID)
    ResponseEntity<ApiResponse<Void>> removeParticipant(
            @PathVariable("conversationId") UUID conversationId,
            @PathVariable("userId") UUID userId);

    @Operation(
            summary = "Leave a conversation",
            description =
                    "Leaves a conversation by setting the caller's own departure timestamp;"
                            + " idempotent. If the caller was a group's last active admin, the"
                            + " oldest remaining active member is promoted. Requires"
                            + " authentication.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Left the conversation"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Caller was never a participant",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @PostMapping(ApiConstants.Messages.ROOT + ApiConstants.Messages.LEAVE)
    ResponseEntity<ApiResponse<Void>> leaveConversation(
            @PathVariable("conversationId") UUID conversationId);
}
