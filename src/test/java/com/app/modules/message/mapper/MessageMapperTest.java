package com.app.modules.message.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.modules.media.enums.MediaType;
import com.app.modules.message.dto.response.MessageMediaResponse;
import com.app.modules.message.dto.response.MessageResponse;
import com.app.modules.message.entity.Message;
import com.app.modules.message.enums.MessageType;

class MessageMapperTest {

    private final MessageMapper mapper = new MessageMapperImpl();

    @Test
    void toMessageResponse_liveMessage_carriesEveryPayload() {
        Message message = message();
        MessageMediaResponse media = media();

        MessageResponse response = mapper.toMessageResponse(message, media);

        assertThat(response.content()).isEqualTo("original text");
        assertThat(response.mediaAssetId()).isEqualTo(message.getMediaAssetId());
        assertThat(response.media()).isEqualTo(media);
        assertThat(response.sharedPostId()).isEqualTo(message.getSharedPostId());
        assertThat(response.isDeleted()).isFalse();
        assertThat(response.deletedAt()).isNull();
    }

    @Test
    void toMessageResponse_administrativelyRemoved_withholdsTextMediaAndShares() {
        OffsetDateTime removedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Message message = message();
        message.setAdminRemovedAt(removedAt);

        MessageResponse response = mapper.toMessageResponse(message, media());

        // A reported image is the usual case, so suppressing only the caption would not be a
        // removal at all.
        assertThat(response.content()).isNull();
        assertThat(response.mediaAssetId()).isNull();
        assertThat(response.media()).isNull();
        assertThat(response.sharedPostId()).isNull();
        assertThat(response.sharedStoryId()).isNull();
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.deletedAt()).isEqualTo(removedAt);
    }

    @Test
    void toMessageResponse_senderDeleted_reportsTheSendersOwnTombstone() {
        OffsetDateTime deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Message message = message();
        message.setContent(null);
        message.setDeleted(true);
        message.setDeletedAt(deletedAt);

        MessageResponse response = mapper.toMessageResponse(message, null);

        assertThat(response.isDeleted()).isTrue();
        assertThat(response.deletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void toMessageResponse_bothTombstones_prefersTheSendersTimestamp() {
        OffsetDateTime deletedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2);
        Message message = message();
        message.setDeleted(true);
        message.setDeletedAt(deletedAt);
        message.setAdminRemovedAt(OffsetDateTime.now(ZoneOffset.UTC));

        MessageResponse response = mapper.toMessageResponse(message, media());

        // The participants already saw the sender's deletion; the moderation timestamp would
        // silently move a tombstone they had been shown.
        assertThat(response.deletedAt()).isEqualTo(deletedAt);
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.content()).isNull();
    }

    @Test
    void toMessageResponse_singleArgument_matchesTheTwoArgumentFormWithNoMedia() {
        Message message = message();

        MessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.media()).isNull();
        assertThat(response.content()).isEqualTo("original text");
    }

    private static Message message() {
        return Message.builder()
                .id(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .messageType(MessageType.IMAGE)
                .content("original text")
                .mediaAssetId(UUID.randomUUID())
                .sharedPostId(UUID.randomUUID())
                .sharedStoryId(UUID.randomUUID())
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private static MessageMediaResponse media() {
        return new MessageMediaResponse(
                UUID.randomUUID(),
                MediaType.IMAGE,
                "https://cdn.example/x.jpg",
                100,
                100,
                null,
                null);
    }
}
